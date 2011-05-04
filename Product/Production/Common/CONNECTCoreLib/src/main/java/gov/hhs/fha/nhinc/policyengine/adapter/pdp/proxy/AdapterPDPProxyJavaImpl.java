/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2011(Year date of delivery) United States Government, as represented by the Secretary of Health and Human Services.  All rights reserved.
 *
 */

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package gov.hhs.fha.nhinc.policyengine.adapter.pdp.proxy;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.JAXBException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.sun.identity.shared.xml.XMLUtils;
import com.sun.identity.xacml.common.XACMLException;
import com.sun.identity.xacml.context.Decision;
import com.sun.identity.xacml.context.Request;
import com.sun.identity.xacml.context.Response;
import com.sun.identity.xacml.context.Result;
import com.sun.identity.xacml.context.Status;
import com.sun.identity.xacml.context.StatusCode;
import com.sun.identity.xacml.context.StatusMessage;
import com.sun.identity.xacml.context.Subject;
import com.sun.identity.xacml.context.Resource;
import com.sun.identity.xacml.context.Attribute;
import com.sun.identity.xacml.context.ContextFactory;

import gov.hhs.fha.nhinc.docrepository.adapter.model.Document;
import gov.hhs.fha.nhinc.docrepository.adapter.model.DocumentQueryParams;
import gov.hhs.fha.nhinc.docrepository.adapter.service.DocumentService;

import java.io.ByteArrayInputStream;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import org.w3c.dom.Element;

/**
 * Java implementation of the adapter PDP service.
 * @author Mastan.Ketha
 */
public class AdapterPDPProxyJavaImpl implements AdapterPDPProxy {

    private Log log = null;
    private String statusCodeValue = "";
    private String statusMessageValue = "";
    private boolean foundMatchingAttributes = false;

    public AdapterPDPProxyJavaImpl() {
        log = createLogger();
    }

    protected Log createLogger() {
        return LogFactory.getLog(getClass());
    }

    /* (non-Javadoc)
     * @see gov.hhs.fha.nhinc.policyengine.adapter.pdp.proxy.AdapterPDPProxy#processPDPRequest(com.sun.identity.xacml.context.Request)
     */
    @Override
    public Response processPDPRequest(Request pdpRequest) {
        log.info("Begin AdapterPDPProxyJavaImpl.processPDPRequest(...)");
        EffectType effect = EffectType.DENY;
        Policy policy = new Policy();
        try {
            DocumentQueryParams params = new DocumentQueryParams();
            String patientId = getResourceIdFromPdpRequest(pdpRequest);
            log.debug("patientid:" + patientId);
            params.setPatientId(patientId);
            List<String> classCodeValues = new ArrayList<String>();
            classCodeValues.add(AdapterPDPConstants.DOCUMENT_CLASS_CODE);
            params.setClassCodes(classCodeValues);
            DocumentService service = new DocumentService();
            List<Document> docs = service.documentQuery(params);
            int docsSize = 0;
            if (docs != null) {
                docsSize = docs.size();
            }
            log.debug("Document size:" + String.valueOf(docsSize));
            String policyStrRawData = "";
            if (docsSize == 1) {
                for (Document doc : docs) {
                    byte[] rawData = doc.getRawData();
                    policyStrRawData = new String(rawData);
                    log.debug("processPDPRequest - Policy rawData:" + policyStrRawData);
                }
            } else if (docsSize < 1) {
                log.info("No policy documents found for the given criteria:");
            } else if (docsSize > 1) {
                log.info("More than one document found for the given criteria:");
            }

            if (policyStrRawData.trim().equals("")) {
                log.info("No Policy info found for the given criteria:");
            } else {
                policy = getPolicyObject(policyStrRawData);
            }
        } catch (Exception ex) {
            effect = EffectType.DENY;
            log.error("Exception occured while retrieving documents");
            log.error(ex.getMessage());
        }

        if (pdpRequest == null) {
            log.info("PDP request is null");
        } else if (policy == null) {
            log.info("Policy is null");
        } else {
            effect = evaluatePolicy(pdpRequest, policy);
            effect = (effect == null) ? EffectType.DENY : effect;
        }
        log.info("processPDPRequest - Policy effect: " + effect.value());
        Response resp = createResponse(effect);
        log.info("End AdapterPDPProxyJavaImpl.processPDPRequest(...)");
        return resp;
    }

    protected Policy getPolicyObject(String policyStrRawData) throws JAXBException {
        log.debug("Begin AdapterPDPProxyJavaImpl.getPolicyObject(...) ***");
        log.debug("getPolicyObject - Policy rawData:" + policyStrRawData);
        Policy policy = new Policy();
        ByteArrayInputStream xmlContentBytes = new ByteArrayInputStream(policyStrRawData.getBytes());
        JAXBContext context = JAXBContext.newInstance("gov.hhs.fha.nhinc.policyengine.adapter.pdp.proxy");
        Unmarshaller unmarshaller = context.createUnmarshaller();
        Object xmlObject = unmarshaller.unmarshal(xmlContentBytes);
        policy = (Policy) xmlObject;
        return policy;
    }

    public String getResourceIdFromPdpRequest(Request pdpRequest) {
        log.debug("Begin AdapterPDPProxyJavaImpl.getPatientIdFromPdpRequest()");
        List<Resource> resources = new ArrayList<Resource>();
        resources = (List<Resource>) pdpRequest.getResources();
        String attrValue = "";
        if (resources != null) {
            log.debug("Resources list size:" + resources.size());
            for (Resource resource : resources) {
                List<Attribute> attributes = new ArrayList<Attribute>();
                attributes = (List<Attribute>) resource.getAttributes();
                log.debug("Attributes list size:" + attributes.size());
                for (Attribute attribute : attributes) {
                    String attrId = "";
                    String attrDataType = "";
                    if (attribute.getAttributeId() != null) {
                        attrId = attribute.getAttributeId().toString();
                        log.debug("AttributeId: " + attrId);
                    } else {
                        log.debug("AttributeId not found in the Attribute");
                    }
                    if (attribute.getDataType() != null) {
                        attrDataType = attribute.getDataType().toString();
                        log.debug("Attribute DataType : " + attrDataType);
                    } else {
                        log.debug("DataType not found in the Attribute");
                    }
                    if ((attrId.trim().equals(AdapterPDPConstants.REQUEST_CONTEXT_ATTRIBUTE_RESOURCEID)) &&
                            attrDataType.trim().equals(AdapterPDPConstants.ATTRIBUTEVALUE_DATATYPE_STRING)) {
                        Element sidElement = (Element) attribute.getAttributeValues().get(0);
                        attrValue = XMLUtils.getElementValue(sidElement);
                        log.debug("Attriute Value: " + attrValue);
                    }
                }
            }
        } else {
            log.info("No resources found in the Request context");
        }
        log.debug("End AdapterPDPProxyJavaImpl.getPatientIdFromPdpRequest()");
        return attrValue;
    }

    private EffectType evaluatePolicy(Request pdpRequest, Policy policy) {
        log.debug("Begin AdapterPDPProxyJavaImpl.evaluatePolicy()");
        boolean isMatch = false;
        statusCodeValue = "";
        statusMessageValue = "";
        EffectType effect = EffectType.DENY;
        try {
            if (policy != null) {
                if (policy.getTarget() == null) {
                    log.info("Policy Target is null. Return Effect value Deny");
                    return EffectType.DENY;
                }
                List<RuleType> rules = new ArrayList<RuleType>();
                rules = policy.getRule();
                if ((rules != null) && (rules.size() > 0)) {
                    log.debug("Rules list size" + rules.size());
                    String policyMatchId = "";
                    String policyAttrValue = "";
                    String policyAttrDataType = "";
                    String policyAttrDesAttrId = "";
                    String policyAttrDesAttrDataType = "";
                    rulesFor:
                    for (RuleType rule : rules) {
                        effect = rule.getEffect();
                        log.debug("Rule Effect value: " + effect);
                        TargetType targetType = new TargetType();
                        targetType = (rule.getTarget() == null) ? policy.getTarget() : rule.getTarget();

                        if (targetType != null) {
                            if (targetType.getSubjects() != null) {
                                List<SubjectType> subjects = new ArrayList<SubjectType>();
                                //subjects = rule.getTarget().getSubjects().getSubject();
                                subjects = targetType.getSubjects().getSubject();
                                if ((subjects != null) && (subjects.size() > 0)) {
                                    log.debug("Subjects list size" + subjects.size());
                                    subjectsFor:
                                    for (SubjectType subject : subjects) {
                                        isMatch = false;
                                        List<SubjectMatchType> subjectMatchs = new ArrayList<SubjectMatchType>();
                                        subjectMatchs = subject.getSubjectMatch();
                                        if ((subjectMatchs != null) && (subjectMatchs.size() > 0)) {
                                            log.debug("subjectMatchs list size" + subjectMatchs.size());
                                            subjectMatchsFor:
                                            for (SubjectMatchType subjectMatch : subjectMatchs) {
                                                policyMatchId = subjectMatch.getMatchId();
                                                log.debug("SubjectMatch MatchId: " + policyMatchId);
                                                policyAttrValue = null;
                                                policyAttrDataType = null;
                                                if (subjectMatch.getAttributeValue() != null) {
                                                    policyAttrValue = (subjectMatch.getAttributeValue().getValue() == null) ? subjectMatch.getAttributeValue().getValue() : subjectMatch.getAttributeValue().getValue().trim();
                                                    policyAttrDataType = (subjectMatch.getAttributeValue().getDataType() == null) ? subjectMatch.getAttributeValue().getDataType() : subjectMatch.getAttributeValue().getDataType().trim();
                                                    log.debug("AttributeValue Value: " + policyAttrValue);
                                                    log.debug("AttributeValue DataType: " + policyAttrDataType);
                                                } else {
                                                    log.debug("AttributeValue is null!");
                                                }
                                                if (subjectMatch.getSubjectAttributeDesignator() != null) {
                                                    policyAttrDesAttrId = (subjectMatch.getSubjectAttributeDesignator().getAttributeId() == null) ? subjectMatch.getSubjectAttributeDesignator().getAttributeId() : subjectMatch.getSubjectAttributeDesignator().getAttributeId().trim();
                                                    policyAttrDesAttrDataType = (subjectMatch.getSubjectAttributeDesignator().getDataType() == null) ? subjectMatch.getSubjectAttributeDesignator().getDataType() : subjectMatch.getSubjectAttributeDesignator().getDataType().trim();
                                                    log.debug("SubjectAttributeDesignator DataType: " + policyAttrDesAttrDataType);
                                                    log.debug("SubjectAttributeDesignator AttributeId: " + policyAttrDesAttrId);
                                                    foundMatchingAttributes = false;
                                                    isMatch = evaluateSubjectMatch(pdpRequest, policyMatchId, policyAttrValue,
                                                            policyAttrDesAttrId, policyAttrDesAttrDataType);
                                                    if (!foundMatchingAttributes) {
                                                        isMatch = false;
                                                        effect = EffectType.DENY;
                                                        statusCodeValue = AdapterPDPConstants.POLICY_RESULT_STATUS_CODE_MISSING_ATTRIBUTE;
                                                        statusMessageValue = AdapterPDPConstants.POLICY_RESULT_STATUS_MESSAGE_MISSING_ATTRIBUTE + " : " +
                                                                policyAttrDesAttrId + " is incorrect or its info is missing in request context";
                                                        break rulesFor;
                                                    }
                                                } else {
                                                    log.debug("SubjectAttributeDesignator is null!");
                                                }
                                                if (!isMatch) {
                                                    break;
                                                }
                                            }
                                        } else {
                                            log.debug("SubjectMatch not found!");
                                        }
                                        if (isMatch) {
                                            break rulesFor;
                                        }
                                    }
                                } else {
                                    log.debug("Rule Subject not found!");
                                }
                            } else {
                                log.debug("Rule Subjects is null");
                                break;
                            }
                        } else {
                            log.debug("Rule Target is null");
                        }
                    }
                } else {
                    log.debug("Rules not found in policy document");
                }
            } else {
                log.info("Policy is null");
            }
        } catch (Exception ex) {
            statusCodeValue = AdapterPDPConstants.POLICY_RESULT_STATUS_CODE_PROCESSING_ERROR;
            statusMessageValue = AdapterPDPConstants.POLICY_RESULT_STATUS_MESSAGE_PROCESSING_ERROR;
            log.error("Exception occured while retrieving documents");
            log.error(ex.getMessage());
        }
        log.debug("End AdapterPDPProxyJavaImpl.evaluatePolicy()");
        log.debug("Rule Effect value: " + effect);
        return effect;
    }

    private boolean evaluateSubjectMatch(Request pdpRequest, String policyMatchId, String policyAttrValue, String policyAttrDesAttrId, String policyAttrDesAttrDataType) {
        log.debug("Begin AdapterPDPProxyJavaImpl.evaluateSubjectMatch()");
        boolean isMatch = false;
        List<Subject> subjects = new ArrayList<Subject>();
        subjects = (List<Subject>) pdpRequest.getSubjects();
        foundMatchingAttributes = false;
        log.debug("evaluateSubjectMatch - policyMatchId: " + policyMatchId);
        log.debug("evaluateSubjectMatch - policyAttrValue: " + policyAttrValue);
        log.debug("evaluateSubjectMatch - policyAttrDesAttrId: " + policyAttrDesAttrId);
        log.debug("evaluateSubjectMatch - policyAttrDesAttrDataType: " + policyAttrDesAttrDataType);
        if (policyMatchId == null) {
            log.debug("Policy - MatchId is null");
        }
        if (policyAttrValue == null) {
            log.debug("Policy - AttrValue is null");
        }
        if (policyAttrDesAttrId == null) {
            log.debug("Policy - AttributeId is null");
        }
        if (policyAttrDesAttrDataType == null) {
            log.debug("Policy - policyAttrDataType is null");
        }
        if (subjects != null) {
            log.debug("Subjects list size:" + subjects.size());
            boolean isAnyAttributeInfoNull = false;
            subjectsFor:
            for (Subject subject : subjects) {
                List<Attribute> attributes = new ArrayList<Attribute>();
                attributes = (List<Attribute>) subject.getAttributes();
                attributesFor:
                for (Attribute attribute : attributes) {
                    String requestAttrId = "";
                    String requestAttrDataType = "";
                    String requestAttrValue = "";
                    isAnyAttributeInfoNull = false;
                    if (attribute.getAttributeId() != null) {
                        requestAttrId = attribute.getAttributeId().toString().trim();
                        log.debug("Request AttributeId: " + requestAttrId);
                    } else {
                        isAnyAttributeInfoNull = true;
                        log.debug("Request AttributeId is null");
                    }
                    if (attribute.getDataType() != null) {
                        requestAttrDataType = attribute.getDataType().toString().trim();
                        log.debug("Request Attribute DataType : " + requestAttrDataType);
                    } else {
                        isAnyAttributeInfoNull = true;
                        log.debug("Request DataType not found in the Attribute");
                    }
                    if (attribute.getAttributeValues() != null) {
                        Element sidElement = (Element) attribute.getAttributeValues().get(0);
                        requestAttrValue = (XMLUtils.getElementValue(sidElement) == null) ? XMLUtils.getElementValue(sidElement) : XMLUtils.getElementValue(sidElement).trim();
                        log.debug("Request Attriute Value: " + requestAttrValue);
                    } else {
                        isAnyAttributeInfoNull = true;
                        log.debug("Request Attriute Value not found in the Attribute");
                    }
                    if (!isAnyAttributeInfoNull) {
                        log.debug("evaluateSubjectMatch - Request AttributeId: " + requestAttrId);
                        log.debug("evaluateSubjectMatch - Request Attribute DataType : " + requestAttrDataType);
                        if ((policyAttrDesAttrId.equals(requestAttrId)) && (policyAttrDesAttrDataType.equals(requestAttrDataType))) {
                            isMatch = evaluateMatchWithFunction(policyMatchId, policyAttrValue, requestAttrValue);
                            foundMatchingAttributes = true;
                        }
                    }
                    log.debug("evaluateSubjectMatch - loop - isMatch: " + isMatch);
                    if (isMatch) {
                        break subjectsFor;
                    }
                }
            }
        } else {
            log.info("No subjects found in the Request context");
        }
        log.debug("evaluateSubjectMatch - isMatch: " + isMatch);
        return isMatch;
    }

    private boolean evaluateMatchWithFunction(String policyMatchId, String policyAttrValue, String requestAttrValue) {
        boolean isMatch = false;
        if (policyMatchId.equals(AdapterPDPConstants.MATCHID_FUNCTION_STRING_EQUAL)) {
            isMatch = evaluateMatchWithStringEqualFunction(policyAttrValue, requestAttrValue);
        } else if (policyMatchId.equals(AdapterPDPConstants.MATCHID_FUNCTION_ANYURI_EQUAL)) {
            isMatch = evaluateMatchWithAnyUriEqualFunction(policyAttrValue, requestAttrValue);
        }
        //log.debug("evaluateMatchWithFunction - isMatch: " + isMatch);
        return isMatch;
    }

    private boolean evaluateMatchWithStringEqualFunction(String policyAttrValue, String requestAttrValue) {
        boolean isMatch = false;
        if ((policyAttrValue == null) || (policyAttrValue.equals(""))) {
            log.debug("Policy Attribute Value is null or empty");
        } else if ((requestAttrValue == null) || (requestAttrValue.equals(""))) {
            log.debug("Request Attribute Value is null or empty");
        } else {
            if (policyAttrValue.trim().equalsIgnoreCase(requestAttrValue)) {
                isMatch = true;
            }
        }
        //log.debug("evaluateMatchWithStringEqualFunction -isMatch: " + isMatch);
        return isMatch;
    }

    private boolean evaluateMatchWithAnyUriEqualFunction(String policyAttrValue, String requestAttrValue) {
        //Need to work on this
        boolean isMatch = false;
        isMatch = evaluateMatchWithStringEqualFunction(policyAttrValue, requestAttrValue);
        //log.debug("evaluateMatchWithAnyUriEqualFunction -isMatch: " + isMatch);

        return isMatch;
    }

    protected Response createResponse(EffectType effect) {
        Response response = null;
        try {
            response = ContextFactory.getInstance().createResponse();
            response.addResult(createResult(effect));
            log.debug("response-xml:" + response.toXMLString());
        } catch (XACMLException e) {
            log.error("Error adding a result: " + e.getMessage(), e);
        } catch (URISyntaxException u) {
            log.error("Error adding a result: " + u.getMessage(), u);
        }

        return response;
    }

    protected Result createResult(EffectType effect) throws URISyntaxException {
        log.info("Begin AdapterPDPProxyJavaImpl.createResult(...)");
        Result result = null;
        try {
            Decision decision = ContextFactory.getInstance().createDecision();
            Status status = ContextFactory.getInstance().createStatus();
            StatusCode statusCode = ContextFactory.getInstance().createStatusCode();
            StatusMessage statusMessage = ContextFactory.getInstance().createStatusMessage();
            result = ContextFactory.getInstance().createResult();

            if (statusCodeValue.equals("")) {
                statusCodeValue = AdapterPDPConstants.POLICY_RESULT_STATUS_CODE_OK;
                statusMessageValue = AdapterPDPConstants.POLICY_RESULT_STATUS_MESSAGE_OK;
            }

            statusMessageValue = (statusMessageValue == null) ? "" : statusMessageValue;
            statusCode.setValue(statusCodeValue);
            statusMessage.setValue(statusMessageValue);

            decision.setValue(effect.value());
            status.setStatusCode(statusCode);
            status.setStatusMessage(statusMessage);
            result.setStatus(status);
            result.setDecision(decision);

        } catch (XACMLException e) {
            log.error("Error in setting decision and status: " + e.getMessage(), e);
        }
        log.info("End AdapterPDPProxyJavaImpl.createResult(...)");
        return result;
    }
}