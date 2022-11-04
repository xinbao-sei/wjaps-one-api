package com.donlim.aps.service;

import com.changhong.sei.core.context.ContextUtil;
import com.changhong.sei.core.dao.BaseEntityDao;
import com.changhong.sei.core.dto.serach.PageResult;
import com.changhong.sei.core.dto.serach.Search;
import com.changhong.sei.core.dto.serach.SearchFilter;
import com.changhong.sei.core.log.LogUtil;
import com.changhong.sei.core.service.BaseEntityService;
import com.donlim.aps.connector.ScmConnector;
import com.donlim.aps.dao.*;
import com.donlim.aps.dto.CalcBomDto;
import com.donlim.aps.dto.MaterialRequireDto;
import com.donlim.aps.dto.ScmXbDeliveryQueryDto;
import com.donlim.aps.entity.*;
import com.donlim.aps.entity.cust.OrderChangeCountVO;
import com.donlim.aps.util.BomUtil;
import com.donlim.aps.util.CompanyEnum;
import com.donlim.aps.util.DateUtils;
import org.apache.commons.lang.StringUtils;
import org.hibernate.query.internal.NativeQueryImpl;
import org.hibernate.transform.Transformers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;


/**
 * scm送货需求(ScmXbDelivery)业务逻辑实现类
 *
 * @author sei
 * @since 2022-05-18 08:12:55
 */
@Service
public class ScmXbDeliveryService extends BaseEntityService<ScmXbDelivery> {
    @Autowired
    private ScmXbDeliveryDao dao;
    @Autowired
    private ScmXbDeliveryPlanService scmXbDeliveryPlanService;
    @Autowired
    private ScmXbDeliveryPlanDao scmXbDeliveryPlanDao;
    @Autowired
    private ApsPurchasePlanService apsPurchasePlanService;
    @Autowired
    private U9MaterialDao u9MaterialDao;
    @Autowired
    private U9PurchaseDao u9PurchaseDao;
    @Autowired
    private U9BomDao u9BomDao;
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    protected BaseEntityDao<ScmXbDelivery> getDao() {
        return dao;
    }

    /**
     * 根据采购单行ID查找送SCM送货单
     *
     * @param poLineIds
     * @return
     */
    public List<ScmXbDelivery> findByPoLineIdIn(List<Long> poLineIds) {
        return dao.findByPoLineIdIn(poLineIds);
    }

    /**
     * 更新SCM送货订单信息
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateOrderTask() {
        try {
            // 自产SCM
            LocalDate now = LocalDate.now();
            List<ScmXbDelivery> list = ScmConnector.getDeliveryData(CompanyEnum.WJ2_SCM.getCode(), now);
            // 委外SCM
            //List<ScmXbDelivery> purchaseList = ScmConnector.getPurchaseData(CompanyEnum.WJ2_SCM.getName(), LocalDate.now());
            //list.addAll(purchaseList);
            List<Long> polineIds = list.stream().map(ScmXbDelivery::getPoLineId).collect(Collectors.toList());
            List<ScmXbDelivery> oldList = dao.findByPoLineIdIn(polineIds);
            for (ScmXbDelivery newScmXbDelivery : list) {
                Optional<ScmXbDelivery> oldScmXbDelivery = oldList.stream().filter(p -> p.getPoLineId() == newScmXbDelivery.getPoLineId()).findFirst();
                if (oldScmXbDelivery.isPresent()) {
                    ScmXbDelivery old = oldScmXbDelivery.get();
                    old.setPoLineNo(newScmXbDelivery.getPoLineNo());
                    //当日期和数量发生变更，记录下来
                    if (old.getDeliveryQty().compareTo(newScmXbDelivery.getDeliveryQty()) != 0) {
                        old.setDeliveryOldQty(oldScmXbDelivery.get().getDeliveryQty());
                        old.setDeliveryQty(newScmXbDelivery.getDeliveryQty());
                        old.setChangeQtyFlag(true);
                    }
                    if (old.getDeliveryStartDate().isAfter(newScmXbDelivery.getDeliveryStartDate()) || old.getDeliveryStartDate().isBefore(newScmXbDelivery.getDeliveryStartDate())) {
                        old.setDeliveryOldStartDate(oldScmXbDelivery.get().getDeliveryStartDate());
                        old.setDeliveryStartDate(newScmXbDelivery.getDeliveryStartDate());
                        old.setChangeDateFlag(true);
                    }
                    if (old.getDeliveryEndDate().isAfter(newScmXbDelivery.getDeliveryEndDate()) || old.getDeliveryEndDate().isBefore(newScmXbDelivery.getDeliveryEndDate())) {
                        old.setDeliveryEndDate(newScmXbDelivery.getDeliveryEndDate());
                    }
                } else {
                    oldList.add(newScmXbDelivery);
                }
            }
            dao.save(oldList);
            List<String> parentIds = oldList.stream().map(a -> a.getId()).collect(Collectors.toList());
            //更新送货明细
            scmXbDeliveryPlanService.updateDeliveryDetail(CompanyEnum.WJ2_SCM, LocalDate.now(), parentIds);
            //生成采购委外计划
            LogUtil.bizLog("开始生成采购计划");
            calcPurchaseDeliveryDate(now);
            LogUtil.bizLog("结束生成采购计划");
            //下载APS计划
            // oneApsPlanDataService.updateOneApsData(LocalDate.now());
        } catch (Exception e) {
            LogUtil.bizLog("生成采购计划失败", e);
        }
    }

    public PageResult<OrderChangeCountVO> queryOrderChangeCount(Search search) {
        StringBuilder countSqlBuilder = new StringBuilder();
        countSqlBuilder.append("select count(*) from scm_xb_delivery a left join aps_order b on a.id = b.scm_id where ( a.change_date_flag = 1 or a.change_qty_flag = 1 ) ");
        StringBuilder selectSqlBuilder = new StringBuilder();
        selectSqlBuilder.append("select a.id, b.work_group_name as apsOrderWorkGroupName, b.work_line_name as apsOrderWorkLineName ,a.order_no as orderNo, a.po as po, b.material_code as materialCode\n" +
                ", b.material_name as materialName, b.material_spec as materialSpec, a.delivery_qty as deliveryQty, a.delivery_old_qty as deliveryOldQty, a.delivery_start_date as deliveryStartDate\n" +
                ", a.delivery_old_start_date as deliveryOldStartDate,b.status as apsOrderStatus, a.company_name as companyName , a.change_qty_flag as changeQtyFlag , a.change_date_flag as changeDateFlag  \n" +
                " from scm_xb_delivery a left join aps_order b on a.id = b.scm_id where ( a.change_date_flag = 1 or a.change_qty_flag = 1 ) ");
        List<SearchFilter> filters = search.getFilters();
        if (Objects.isNull(filters)) {
            filters = new ArrayList<>();
        }
        //租户代码
        String tenantCode = ContextUtil.getTenantCode();
        if (StringUtils.isNotBlank(tenantCode)) {
            filters.add(new SearchFilter("a.tenant_code", tenantCode, SearchFilter.Operator.EQ));
        }
        if (StringUtils.isNotBlank(search.getQuickSearchValue())) {
            for (String quickSearchProperty : search.getQuickSearchProperties()) {
                filters.add(new SearchFilter(quickSearchProperty, search.getQuickSearchValue(), SearchFilter.Operator.LK));
            }
        }
        StringBuilder whereSqlBuilder = new StringBuilder();
        Map<String, Object> params = new HashMap<>();
        for (SearchFilter filter : filters) {
            if (filter.getValue() instanceof LocalDate) {
                filter.setValue(DateUtils.LocalDateToString((LocalDate) filter.getValue()));
            }
            switch (filter.getOperator()) {
                case EQ:
                    params.put(filter.getFieldName(), filter.getValue());
                    whereSqlBuilder.append(" and " + filter.getFieldName()).append(" =  :" + filter.getFieldName());
                    break;
                case LK:
                    params.put(filter.getFieldName(), "%" + filter.getValue().toString() + "%");
                    whereSqlBuilder.append(" and " + filter.getFieldName()).append(" like  :" + filter.getFieldName());
                    break;
                case LLK:
                    params.put(filter.getFieldName(), filter.getValue().toString() + "%");
                    whereSqlBuilder.append(" and " + filter.getFieldName()).append(" like  :" + filter.getFieldName());
                    break;
                case RLK:
                    params.put(filter.getFieldName(), "%" + filter.getValue().toString());
                    whereSqlBuilder.append(" and " + filter.getFieldName()).append(" like  :" + filter.getFieldName());
                    break;
                case GT:
                    params.put(filter.getFieldName(), filter.getValue());
                    whereSqlBuilder.append(" and " + filter.getFieldName()).append(" >  :" + filter.getFieldName());
                    break;
                case LT:
                    params.put(filter.getFieldName(), filter.getValue());
                    whereSqlBuilder.append(" and " + filter.getFieldName()).append(" <  :" + filter.getFieldName());
                    break;
                case GE:
                    params.put(filter.getFieldName(), filter.getValue());
                    whereSqlBuilder.append(" and " + filter.getFieldName()).append(" >=  :" + filter.getFieldName());
                    break;
                case LE:
                    params.put(filter.getFieldName(), filter.getValue());
                    whereSqlBuilder.append(" and " + filter.getFieldName()).append(" <=  :" + filter.getFieldName());
                    break;
                default:
                    break;
            }

        }
        String countSql = countSqlBuilder.append(whereSqlBuilder).toString();
        Query contQuery = entityManager.createNativeQuery(countSql);
        setParameters(contQuery, params);
        Object singleResult = contQuery.getSingleResult();
        Long count = 0L;
        if (singleResult != null) {
            count = Long.parseLong(singleResult.toString());
        }
        NativeQueryImpl selectQuery = entityManager.createNativeQuery(selectSqlBuilder.append(whereSqlBuilder).toString()).unwrap(NativeQueryImpl.class);
        selectQuery.setResultTransformer(Transformers.aliasToBean(OrderChangeCountVO.class));
        setParameters(selectQuery, params);
        selectQuery.setFirstResult(search.getPageInfo().getPage() - 1);
        selectQuery.setMaxResults(search.getPageInfo().getRows());
        List<OrderChangeCountVO> resultList = selectQuery.getResultList();

        PageResult pageResult = new PageResult();
        pageResult.setRows(resultList);
        Long total = count / search.getPageInfo().getRows();
        Long l = count % search.getPageInfo().getRows();
        if (l > 0) {
            total += 1;
        }
        pageResult.setRecords(count);
        pageResult.setTotal(total.intValue());
        pageResult.setPage(search.getPageInfo().getPage());

        return pageResult;


    }

    private void setParameters(Query query, Map<String, Object> params) {
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            query.setParameter(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 计算采购委外送货时间
     */
    public void calcPurchaseDeliveryDate(LocalDate localDate) {
        List<ScmXbDelivery> scmDate = dao.findByDeliveryStartDateAfter(localDate);
       // scmDate = scmDate.stream().filter(a->a.getOrderNo().equals("Z420BG122002132")).collect(Collectors.toList());
        List<ScmXbDelivery> saleDataList = scmDate.stream().filter(a -> a.getType().equals("1")).collect(Collectors.toList());
       // saleDataList= saleDataList.stream().filter(a->a.getPo().equals("PO-QS220905260")).filter(b->b.getMaterialCode().equals("23300011696")).collect(Collectors.toList());
        List<CalcBomDto> calcBomDtoList = new ArrayList<>();
        for (ScmXbDelivery sale : saleDataList) {
            if(!u9MaterialDao.findByCode(sale.getMaterialCode()).isPresent()){
                continue;
            }
            U9Material u9Material = u9MaterialDao.findByCode(sale.getMaterialCode()).get();
            List<String> canMatch = new ArrayList<>();
                //过滤开立的
                List<U9Purchase> u9PurchaseList = u9PurchaseDao.findAllByDemandCode(sale.getOrderNo()).stream().filter(a -> !a.getStatus().equals("0")).collect(Collectors.toList());
                //查出所有子级物料
                Map<String, List<String>> children = new HashMap<>();
                children.put(u9Material.getId(), null);
                Map<String, List<String>> childrenBom = BomUtil.getChildren(u9Material.getId(), null);
                if (childrenBom != null) {
                    //非直销件
                    children.putAll(childrenBom);
                }
                for (U9Purchase u9Purchase : u9PurchaseList) {
                    if (children.containsKey(u9Purchase.getMaterialId() + "")) {
                        //取出所有上级件计算
                        for (ScmXbDeliveryPlan deliveryPlan : sale.getDeliveryPlans()) {
                            CalcBomDto calcBomDto = new CalcBomDto();
                            LocalDate date = deliveryPlan.getDeliveryDate();
                            BigDecimal qty = deliveryPlan.getQty();
                            if (u9Purchase.getMaterialCode().equals(u9Material.getCode())) {
                                //料自身，不作处理。

                            } else {
                                List<String> materialIds=new ArrayList<>();
                                materialIds.addAll(children.get(u9Purchase.getMaterialId() + "")) ;
                                materialIds.add(u9Purchase.getMaterialId() + "");
                                for (int i = 1; i < materialIds.size(); i++) {
                                    date = date.minusDays((u9MaterialDao.findById(materialIds.get(i)).get().getFixedLt().longValue()));
                                    Long materialId = Long.parseLong(materialIds.get(i));
                                    Optional<U9Bom> first = u9BomDao.findByMasterId(materialIds.get(i - 1)).stream().filter(a -> a.getMaterialId().equals(materialId)).findFirst();
                                    if (first.isPresent()) {
                                        qty = first.get().getQty().multiply(qty);
                                    } else {
                                        LogUtil.bizLog("出错");
                                    }
                                }
                            }
                            date = date.minusDays(u9Material.getFixedLt().longValue());
                            calcBomDto.setU9Purchase(u9Purchase);
                            calcBomDto.setDocNo(u9Purchase.getDocNo());
                            calcBomDto.setPlanDate(date);
                            calcBomDto.setDeliveryStartDate(sale.getDeliveryStartDate());
                            calcBomDto.setDeliveryEndDate(sale.getDeliveryEndDate());
                            calcBomDto.setQty(qty);
                            calcBomDto.setMaterialId(u9Purchase.getMaterialId() + "");
                            calcBomDto.setMaterialCode(u9Purchase.getMaterialCode());
                            calcBomDtoList.add(calcBomDto);
                        }
                    }
                }

        }
        if (calcBomDtoList.size() > 0) {
           apsPurchasePlanService.calcPurchasePlan(calcBomDtoList, localDate);
        }


    }


    /**
     * 获取某日开始后的需求分类号
     *
     * @param date
     * @return
     */
    public List findOrderNum(LocalDate date) {
        return dao.findOrderNum(date);
    }

    public List<ScmXbDelivery> findAllByOrderNo(String orderNo) {
        return dao.findAllByOrderNo(orderNo);
    }

    /**
     * 获取物料需求（递归）
     *
     * @param search
     * @return
     */
    public List<Map<String, Object>> getRequire(Search search) {
        Map<String, Map<String, Object>> resultMap = new HashMap<>();
        List<SearchFilter> filters = search.getFilters();
        ScmXbDeliveryQueryDto queryDto = new ScmXbDeliveryQueryDto();
        String materialCodeFilter = "";
        String materialNameFilter = "";
        String materialSpecFilter = "";
        for (SearchFilter filter : filters) {
            if (filter.getFieldName().equals("orderNo")) {
                queryDto.setOrderNo(filter.getValue().toString());
            }
            if (filter.getFieldName().equals("materialCode")) {
                //queryDto.setMaterialCode(filter.getValue().toString());
                materialCodeFilter = filter.getValue().toString();
            }
            if (filter.getFieldName().equals("materialName")) {
                //queryDto.setMaterialName(filter.getValue().toString());
                materialNameFilter = filter.getValue().toString();
            }
            if (filter.getFieldName().equals("materialSpec")) {
                //queryDto.setMaterialSpec(filter.getValue().toString());
                materialSpecFilter = filter.getValue().toString();
            }

            if (filter.getFieldName().equals("planDate")) {
                switch (filter.getOperator()) {
                    case EQ:
                        queryDto.setStartDate(DateUtils.date2LocalDate((Date) filter.getValue()));
                        queryDto.setEndDate(DateUtils.date2LocalDate((Date) filter.getValue()));
                        break;
                    case GE:
                        queryDto.setStartDate(DateUtils.date2LocalDate((Date) filter.getValue()));
                        break;
                    case LE:
                        queryDto.setEndDate(DateUtils.date2LocalDate((Date) filter.getValue()));
                        break;
                    default:
                        break;
                }
            }
        }
        if (queryDto.getStartDate() == null) {
            queryDto.setEndDate(LocalDate.now());
            queryDto.setStartDate(LocalDate.now());
        }
        queryDto.setSupplierCode(CompanyEnum.WJ2_SCM.getCode());
        List<ScmXbDelivery> deliveryRecordList = dao.queryDelivery(queryDto);
        List<MaterialRequireDto> requireList = new ArrayList<>();
        for (ScmXbDelivery scmXbDelivery : deliveryRecordList) {
            List<MaterialRequireDto> requireAddList = new ArrayList<>();
            MaterialRequireDto materialRequireDto = new MaterialRequireDto();
            materialRequireDto.setMaterialCode(scmXbDelivery.getMaterialCode());
            materialRequireDto.setMaterialName(scmXbDelivery.getMaterialName());
            materialRequireDto.setMaterialSpec(scmXbDelivery.getSpec());
            materialRequireDto.setRequireQty(scmXbDelivery.getDeliveryQty());
            materialRequireDto.setRequireDate(scmXbDelivery.getDeliveryStartDate());
            materialRequireDto.setPo(scmXbDelivery.getPo());
            materialRequireDto.setOrderNo(scmXbDelivery.getOrderNo());
            requireAddList.add(materialRequireDto);
            requireAddList = getBom(materialRequireDto, requireAddList);
            requireList.addAll(requireAddList);

        }
        if (materialCodeFilter != "") {
            String finalMaterialCodeFilter = materialCodeFilter;
            requireList = requireList.stream().filter(a -> a.getMaterialCode().equals(finalMaterialCodeFilter)).collect(Collectors.toList());
        }
        if (materialNameFilter != "") {
            String finalmaterialNameFilter = materialNameFilter;
            requireList = requireList.stream().filter(a -> a.getMaterialName().equals(finalmaterialNameFilter)).collect(Collectors.toList());
        }
        if (materialSpecFilter != "") {
            String finalmaterialSpecFilter = materialSpecFilter;
            requireList = requireList.stream().filter(a -> a.getMaterialName().equals(finalmaterialSpecFilter)).collect(Collectors.toList());
        }

        for (MaterialRequireDto materialRequireDto : requireList) {

            String strRequireDate = DateUtils.LocalDateToString(materialRequireDto.getRequireDate());
            //key=单号+料号+日期，相同的数量合并
            String key = materialRequireDto.getOrderNo() + materialRequireDto.getMaterialCode() + strRequireDate;
            Map<String, Object> row = resultMap.get(key);
            if (row == null) {
                row = new HashMap<>();
                row.put("orderNo", materialRequireDto.getOrderNo());
                row.put("materialCode", materialRequireDto.getMaterialCode());
                row.put("materialName", materialRequireDto.getMaterialName());
                row.put("materialSpec", materialRequireDto.getMaterialSpec());
                row.put("po", materialRequireDto.getPo());
                row.put("id", UUID.randomUUID().toString());
                row.put(DateUtils.LocalDateToString(materialRequireDto.getRequireDate()), materialRequireDto.getRequireQty());
                resultMap.put(key, row);
            } else {
                row.put(strRequireDate, new BigDecimal(row.get(strRequireDate).toString()).add(materialRequireDto.getRequireQty()));
            }
        }
        return new ArrayList<>(resultMap.values());
    }

    /**
     * 递归获取料号下的所有料号
     *
     * @param requireDto
     * @param requireList
     * @return
     */
    private List<MaterialRequireDto> getBom(MaterialRequireDto requireDto, List<MaterialRequireDto> requireList) {
        Optional<U9Material> material = u9MaterialDao.findByCode(requireDto.getMaterialCode());
        List<U9Bom> u9BomList = u9BomDao.findByMasterId(material.get().getId());
        if (u9BomList.size() == 0) {
            LogUtil.bizLog("料号" + material.get().getCode() + "没有建BOM");
        }
        for (U9Bom u9Bom : u9BomList) {
            MaterialRequireDto materialRequireDto = new MaterialRequireDto();
            //需求量=用量*送货量
            materialRequireDto.setRequireQty((u9Bom.getQty().multiply(requireDto.getRequireQty())).setScale(4));
            materialRequireDto.setMaterialCode(u9Bom.getMaterial().getCode());
            materialRequireDto.setMaterialName(u9Bom.getMaterial().getName());
            materialRequireDto.setMaterialSpec(u9Bom.getMaterial().getSpec());
            materialRequireDto.setRequireDate(requireDto.getRequireDate());
            materialRequireDto.setPo(requireDto.getPo());
            materialRequireDto.setOrderNo(requireDto.getOrderNo());
            requireList.add(materialRequireDto);
            getBom(materialRequireDto, requireList);
        }
        return requireList;
    }


}
