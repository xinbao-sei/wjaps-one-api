
package com.donlim.aps.webservice.aps;

import javax.xml.namespace.QName;
import javax.xml.ws.*;
import java.net.MalformedURLException;
import java.net.URL;


/**
 * This class was generated by the JAX-WS RI.
 * JAX-WS RI 2.2.9-b130926.1035
 * Generated source version: 2.2
 *
 */
@WebServiceClient(name = "DONLIM_APS_QUERYSYNC", targetNamespace = "http://www.example.org/DONLIM_APS_QUERYSYNC/", wsdlLocation = "http://10.233.0.138:7801/WS/DONLIM/APS/DONLIM_APS_QUERYSYNC?wsdl")
public class DONLIMAPSQUERYSYNC_Service
    extends Service
{

    private final static URL DONLIMAPSQUERYSYNC_WSDL_LOCATION;
    private final static WebServiceException DONLIMAPSQUERYSYNC_EXCEPTION;
    private final static QName DONLIMAPSQUERYSYNC_QNAME = new QName("http://www.example.org/DONLIM_APS_QUERYSYNC/", "DONLIM_APS_QUERYSYNC");

    static {
        URL url = null;
        WebServiceException e = null;
        try {
            url = new URL("http://10.233.0.138:7801/WS/DONLIM/APS/DONLIM_APS_QUERYSYNC?wsdl");
        } catch (MalformedURLException ex) {
            e = new WebServiceException(ex);
        }
        DONLIMAPSQUERYSYNC_WSDL_LOCATION = url;
        DONLIMAPSQUERYSYNC_EXCEPTION = e;
    }

    public DONLIMAPSQUERYSYNC_Service() {
        super(__getWsdlLocation(), DONLIMAPSQUERYSYNC_QNAME);
    }

    public DONLIMAPSQUERYSYNC_Service(WebServiceFeature... features) {
        super(__getWsdlLocation(), DONLIMAPSQUERYSYNC_QNAME, features);
    }

    public DONLIMAPSQUERYSYNC_Service(URL wsdlLocation) {
        super(wsdlLocation, DONLIMAPSQUERYSYNC_QNAME);
    }

    public DONLIMAPSQUERYSYNC_Service(URL wsdlLocation, WebServiceFeature... features) {
        super(wsdlLocation, DONLIMAPSQUERYSYNC_QNAME, features);
    }

    public DONLIMAPSQUERYSYNC_Service(URL wsdlLocation, QName serviceName) {
        super(wsdlLocation, serviceName);
    }

    public DONLIMAPSQUERYSYNC_Service(URL wsdlLocation, QName serviceName, WebServiceFeature... features) {
        super(wsdlLocation, serviceName, features);
    }

    /**
     *
     * @return
     *     returns DONLIMAPSQUERYSYNC
     */
    @WebEndpoint(name = "DONLIM_APS_QUERYSYNCSOAP")
    public DONLIMAPSQUERYSYNC getDONLIMAPSQUERYSYNCSOAP() {
        return super.getPort(new QName("http://www.example.org/DONLIM_APS_QUERYSYNC/", "DONLIM_APS_QUERYSYNCSOAP"), DONLIMAPSQUERYSYNC.class);
    }

    /**
     *
     * @param features
     *     A list of {@link WebServiceFeature} to configure on the proxy.  Supported features not in the <code>features</code> parameter will have their default values.
     * @return
     *     returns DONLIMAPSQUERYSYNC
     */
    @WebEndpoint(name = "DONLIM_APS_QUERYSYNCSOAP")
    public DONLIMAPSQUERYSYNC getDONLIMAPSQUERYSYNCSOAP(WebServiceFeature... features) {
        return super.getPort(new QName("http://www.example.org/DONLIM_APS_QUERYSYNC/", "DONLIM_APS_QUERYSYNCSOAP"), DONLIMAPSQUERYSYNC.class, features);
    }

    private static URL __getWsdlLocation() {
        if (DONLIMAPSQUERYSYNC_EXCEPTION!= null) {
            throw DONLIMAPSQUERYSYNC_EXCEPTION;
        }
        return DONLIMAPSQUERYSYNC_WSDL_LOCATION;
    }

}
