package com.gba.merchantbao.shipment.webservice;

import com.gba.merchantbao.auth.exception.AuthError;
import com.gba.merchantbao.shipment.dto.ShipmentView;
import com.gba.merchantbao.shipment.service.ShipmentQueryService;

import jakarta.jws.WebService;
import jakarta.xml.soap.SOAPConstants;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPFactory;
import jakarta.xml.soap.SOAPFault;
import jakarta.xml.ws.soap.SOAPFaultException;

import javax.xml.namespace.QName;

/**
 * SOAP endpoint implementation published at /services/shipment (WSDL: /services/shipment?wsdl).
 * Credential validation is enforced by ShipmentServiceTokenHandler before any business call;
 * data comes from the same ShipmentQueryService as REST.
 */
@WebService(serviceName = "ShipmentWebService", portName = "ShipmentWebServicePort",
        targetNamespace = ShipmentWebServicePort.NAMESPACE,
        endpointInterface = "com.gba.merchantbao.shipment.webservice.ShipmentWebServicePort")
@org.springframework.stereotype.Service
public class ShipmentWebService implements ShipmentWebServicePort {

    private final ShipmentQueryService shipments;

    public ShipmentWebService(ShipmentQueryService shipments) {
        this.shipments = shipments;
    }

    @Override
    public String ping() {
        return "gba-merchant-bao shipment WebService v1";
    }

    @Override
    public ShipmentSummary queryShipmentByOrderNo(String merchantCode, String orderNo) {
        if (merchantCode == null || merchantCode.isBlank() || orderNo == null || orderNo.isBlank()) {
            throw fault("invalid argument");
        }
        try {
            ShipmentView view = shipments.getForMerchantCode(merchantCode.trim(), orderNo.trim());
            return ShipmentSummary.from(view);
        } catch (AuthError e) {
            if (e.status() == 404) {
                throw fault("NOT_FOUND");
            }
            throw fault("internal server error");
        }
    }

    private static SOAPFaultException fault(String message) {
        try {
            SOAPFactory factory = SOAPFactory.newInstance();
            SOAPFault fault = factory.createFault(message, SOAPConstants.SOAP_SENDER_FAULT);
            fault.addDetail().addDetailEntry(
                    new QName(ShipmentWebServicePort.NAMESPACE, message, "shipment"));
            return new SOAPFaultException(fault);
        } catch (SOAPException e) {
            throw new IllegalStateException("Unable to build SOAP fault", e);
        }
    }
}
