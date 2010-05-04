/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package gov.hhs.fha.nhinc.patientdiscovery.async.response;

import gov.hhs.fha.nhinc.async.AsyncMessageIdExtractor;
import gov.hhs.fha.nhinc.asyncmsgs.dao.AsyncMsgRecordDao;
import gov.hhs.fha.nhinc.asyncmsgs.model.AsyncMsgRecord;
import gov.hhs.fha.nhinc.common.nhinccommon.AcknowledgementType;
import gov.hhs.fha.nhinc.common.nhinccommon.AssertionType;
import gov.hhs.fha.nhinc.nhinclib.NhincConstants;
import gov.hhs.fha.nhinc.nhinclib.NullChecker;
import gov.hhs.fha.nhinc.patientdiscovery.NhinPatientDiscoveryUtils;
import gov.hhs.fha.nhinc.patientdiscovery.PatientDiscovery201306Processor;
import gov.hhs.fha.nhinc.patientdiscovery.PatientDiscoveryAdapterSender;
import gov.hhs.fha.nhinc.patientdiscovery.PatientDiscoveryAuditLogger;
import gov.hhs.fha.nhinc.patientdiscovery.PatientDiscoveryPolicyChecker;
import gov.hhs.fha.nhinc.patientdiscovery.response.ResponseFactory;
import gov.hhs.fha.nhinc.patientdiscovery.response.TrustMode;
import gov.hhs.fha.nhinc.patientdiscovery.response.VerifyMode;
import gov.hhs.fha.nhinc.saml.extraction.SamlTokenExtractor;
import gov.hhs.fha.nhinc.transform.marshallers.JAXBContextHandler;
import gov.hhs.fha.nhinc.transform.subdisc.HL7AckTransforms;
import java.sql.Blob;
import java.util.List;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Unmarshaller;
import javax.xml.ws.WebServiceContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hl7.v3.II;
import org.hl7.v3.MCCIIN000002UV01;
import org.hl7.v3.PRPAIN201306UV02;

/**
 *
 * @author JHOPPESC
 */
public class NhinPatientDiscoveryAsyncRespImpl {

    private static Log log = LogFactory.getLog(NhinPatientDiscoveryAsyncRespImpl.class);

    public MCCIIN000002UV01 respondingGatewayPRPAIN201306UV02(PRPAIN201306UV02 body, WebServiceContext context) {
        MCCIIN000002UV01 resp = new MCCIIN000002UV01();

        AssertionType assertion = SamlTokenExtractor.GetAssertion(context);

        // Extract the message id value from the WS-Addressing Header and place it in the Assertion Class
        if (assertion != null) {
            AsyncMessageIdExtractor msgIdExtractor = new AsyncMessageIdExtractor();
            assertion.setAsyncMessageId(msgIdExtractor.GetAsyncRelatesTo(context));
        }

        // Audit the incoming Nhin 201306 Message
        PatientDiscoveryAuditLogger auditLogger = new PatientDiscoveryAuditLogger();
        AcknowledgementType ack = auditLogger.auditNhin201306(body, assertion, NhincConstants.AUDIT_LOG_INBOUND_DIRECTION);

        resp = respondingGatewayPRPAIN201306UV02(body, assertion);

        // Audit the responding ack Message
        ack = auditLogger.auditAck(resp, assertion, NhincConstants.AUDIT_LOG_OUTBOUND_DIRECTION, NhincConstants.AUDIT_LOG_NHIN_INTERFACE);

        return resp;
    }

    public MCCIIN000002UV01 respondingGatewayPRPAIN201306UV02(PRPAIN201306UV02 body, AssertionType assertion) {
        MCCIIN000002UV01 resp = new MCCIIN000002UV01();
        String ackMsg = "Success";
        

        // Check if the Patient Discovery Async Response Service is enabled
        if (isServiceEnabled()) {
            // Perform a policy check
            if (checkPolicy(body, assertion)) {

                // Store AA to HCID Mapping
                storeMapping (body);

                // Obtain the response mode in order to determine how the message is to be processed
                int respModeType = getResponseMode();

                if (respModeType == ResponseFactory.PASSTHRU_MODE) {
                    // Nothing to do here, empty target to cover the passthrough case
                } else if (respModeType == ResponseFactory.TRUST_MODE) {
                    processRespTrustMode(body, assertion);
                } else {
                    // Default is Verify Mode
                    processRespVerifyMode(body, assertion);
                }

                resp = sendToAdapter(body, assertion);
            }
            else {
                ackMsg = "Policy Check Failed";
                log.error(ackMsg);
                resp = HL7AckTransforms.createAckFrom201306(body, ackMsg);
            }
        } else {
            ackMsg = "Patient Discovery Async Response Service Not Enabled";
            log.error(ackMsg);
            resp = HL7AckTransforms.createAckFrom201306(body, ackMsg);
        }


        return resp;
    }

    protected int getResponseMode () {
        ResponseFactory respFactory = new ResponseFactory();
        
        return respFactory.getResponseModeType();
    }

    /**
     * Checks the gateway.properties file to see if the Patient Discovery Async Response Service is enabled.
     *
     * @return Returns true if the servicePatientDiscoveryAsyncReq is enabled in the properties file.
     */
    protected boolean isServiceEnabled() {
        return NhinPatientDiscoveryUtils.isServiceEnabled(NhincConstants.NHINC_PATIENT_DISCOVERY_ASYNC_REQ_SERVICE_NAME);
    }

    protected MCCIIN000002UV01 sendToAdapter(PRPAIN201306UV02 body, AssertionType assertion) {
        PatientDiscoveryAuditLogger auditLogger = new PatientDiscoveryAuditLogger();
        AcknowledgementType ack = auditLogger.auditAdapter201306(body, assertion, NhincConstants.AUDIT_LOG_OUTBOUND_DIRECTION);

        PatientDiscoveryAdapterSender adapterSender = new PatientDiscoveryAdapterSender();

        MCCIIN000002UV01 resp = adapterSender.sendAsyncRespToAgency(body, assertion);

        ack = auditLogger.auditAck(resp, assertion, NhincConstants.AUDIT_LOG_INBOUND_DIRECTION, NhincConstants.AUDIT_LOG_ADAPTER_INTERFACE);

        return resp;
    }

    protected void processRespVerifyMode(PRPAIN201306UV02 body, AssertionType assertion) {
        // In Verify Mode:
        //    1)  Query MPI to verify the patient is a match.
        //    2)  If a match is found in MPI then proceed with the correlation
        //
        // Note: Currently only the message from the Nhin is returned to the Agency so there is no
        //       need for this method to return a value.
        VerifyMode respProcessor = new VerifyMode();
        PRPAIN201306UV02 resp = respProcessor.processResponse(body, assertion);
    }

    protected void processRespTrustMode(PRPAIN201306UV02 body, AssertionType assertion) {
        // In Trust Mode:
        //    1)  Query async database for a record corresponding to the message/relatesto id
        //    2)  If a record is found then proceed with correlation
        //
        // Note: Currently only the message from the Nhin is returned to the Agency so there is no
        //       need for this method to return a value.
        II patId = new II();
        AsyncMsgRecordDao asyncDbDao = new AsyncMsgRecordDao();

        List<AsyncMsgRecord> asyncMsgRecs = asyncDbDao.queryByMessageId(assertion.getAsyncMessageId());

        if (NullChecker.isNotNullish(asyncMsgRecs)) {
            AsyncMsgRecord dbRec = asyncMsgRecs.get(0);
            patId = extractPatId(dbRec.getMsgData());

            TrustMode respProcessor = new TrustMode();
            PRPAIN201306UV02 resp = respProcessor.processResponse(body, assertion, patId);

            // TODO: Clean up database entry
        }
    }

    private II extractPatId(Blob msgData) {
        II patId = new II();

        patId.setExtension("1234");
        patId.setRoot("1.1");

//        if (msgData != null) {
//            try {
//                JAXBContextHandler oHandler = new JAXBContextHandler();
//                JAXBContext jc = JAXBContext.newInstance("org.hl7.v3");
//                Unmarshaller unmarshaller = jc.createUnmarshaller();
//                patId = (II) unmarshaller.unmarshal(msgData.getBinaryStream());
//
//                log.debug("Patient Id Retrieved From the Database: " + patId.getExtension() + " " + patId.getRoot());
//            } catch (Exception e) {
//                e.printStackTrace();
//                throw new RuntimeException();
//            }
//        } else {
//            log.error("Message Data contained in the database was null");
//        }

        return patId;
    }

    protected boolean checkPolicy(PRPAIN201306UV02 response, AssertionType assertion) {
        PatientDiscoveryPolicyChecker policyChecker = new PatientDiscoveryPolicyChecker();

        II patIdOverride = new II();

        if (NullChecker.isNotNullish(response.getControlActProcess().getSubject()) &&
                response.getControlActProcess().getSubject().get(0) != null &&
                response.getControlActProcess().getSubject().get(0).getRegistrationEvent() != null &&
                response.getControlActProcess().getSubject().get(0).getRegistrationEvent().getSubject1() != null &&
                response.getControlActProcess().getSubject().get(0).getRegistrationEvent().getSubject1().getPatient() != null &&
                NullChecker.isNotNullish(response.getControlActProcess().getSubject().get(0).getRegistrationEvent().getSubject1().getPatient().getId()) &&
                response.getControlActProcess().getSubject().get(0).getRegistrationEvent().getSubject1().getPatient().getId().get(0) != null &&
                NullChecker.isNotNullish(response.getControlActProcess().getSubject().get(0).getRegistrationEvent().getSubject1().getPatient().getId().get(0).getExtension()) &&
                NullChecker.isNotNullish(response.getControlActProcess().getSubject().get(0).getRegistrationEvent().getSubject1().getPatient().getId().get(0).getRoot())) {
            patIdOverride.setExtension(response.getControlActProcess().getSubject().get(0).getRegistrationEvent().getSubject1().getPatient().getId().get(0).getExtension());
            patIdOverride.setRoot(response.getControlActProcess().getSubject().get(0).getRegistrationEvent().getSubject1().getPatient().getId().get(0).getRoot());
        } else {
            patIdOverride = null;
        }

        return policyChecker.check201305Policy(response, patIdOverride, assertion);
    }

    protected void storeMapping (PRPAIN201306UV02 msg) {
        PatientDiscovery201306Processor msgProcessor = new PatientDiscovery201306Processor();
        msgProcessor.storeMapping(msg);
    }
}
