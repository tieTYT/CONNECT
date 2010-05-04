package gov.hhs.fha.nhinc.patientdiscovery.entity;

import gov.hhs.fha.nhinc.common.nhinccommon.AssertionType;
import gov.hhs.fha.nhinc.common.nhinccommon.NhinTargetCommunitiesType;
import gov.hhs.fha.nhinc.common.nhinccommon.NhinTargetSystemType;
import gov.hhs.fha.nhinc.connectmgr.ConnectionManagerCache;
import gov.hhs.fha.nhinc.connectmgr.ConnectionManagerException;
import gov.hhs.fha.nhinc.connectmgr.data.CMUrlInfo;
import gov.hhs.fha.nhinc.connectmgr.data.CMUrlInfos;
import gov.hhs.fha.nhinc.nhinclib.NhincConstants;
import gov.hhs.fha.nhinc.patientdiscovery.PatientDiscovery201305Processor;
import gov.hhs.fha.nhinc.patientdiscovery.PatientDiscovery201306Processor;
import gov.hhs.fha.nhinc.patientdiscovery.PatientDiscoveryAuditLogger;
import gov.hhs.fha.nhinc.patientdiscovery.PatientDiscoveryPolicyChecker;
import gov.hhs.fha.nhinc.patientdiscovery.proxy.NhincProxyPatientDiscoverySecuredImpl;
import gov.hhs.fha.nhinc.patientdiscovery.response.ResponseFactory;
import gov.hhs.fha.nhinc.patientdiscovery.response.ResponseParams;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hl7.v3.CommunityPRPAIN201306UV02ResponseType;
import org.hl7.v3.PRPAIN201305UV02;
import org.hl7.v3.PRPAIN201306UV02;
import org.hl7.v3.ProxyPRPAIN201305UVProxySecuredRequestType;
import org.hl7.v3.RespondingGatewayPRPAIN201305UV02RequestType;
import org.hl7.v3.RespondingGatewayPRPAIN201306UV02ResponseType;

/**
 *
 * @author Neil Webb
 */
public class EntityPatientDiscoveryProcessor
{
    private Log log = null;

    public EntityPatientDiscoveryProcessor()
    {
        log = createLogger();
    }

    protected Log createLogger()
    {
        return LogFactory.getLog(getClass());
    }

    public RespondingGatewayPRPAIN201306UV02ResponseType respondingGatewayPRPAIN201305UV02(RespondingGatewayPRPAIN201305UV02RequestType request, AssertionType assertion)
    {
        log.debug("Begin respondingGatewayPRPAIN201305UV02");
        RespondingGatewayPRPAIN201306UV02ResponseType response = null;

        if(request == null)
        {
            log.warn("RespondingGatewayPRPAIN201305UV02RequestType was null.");
        }
        else if(assertion == null)
        {
            log.warn("AssertionType was null.");
        }
        else if(request.getPRPAIN201305UV02() == null)
        {
            log.warn("PRPAIN201305UV02 was null.");
        }
        else if(request.getNhinTargetCommunities() == null)
        {
            log.warn("NhinTargetCommunitiesType was null.");
        }
        else
        {
            logEntityPatientDiscoveryRequest(request, assertion);

            response = getResponseFromCommunities(request, assertion);

            logAggregatedResponseFromNhin(response, assertion);
        }
        log.debug("End respondingGatewayPRPAIN201305UV02");
        return response;
    }

    protected CMUrlInfos getEndpoints(NhinTargetCommunitiesType targetCommunities)
    {
        CMUrlInfos urlInfoList = null;
        if(targetCommunities  != null)
        {
            try
            {
                urlInfoList = ConnectionManagerCache.getEndpontURLFromNhinTargetCommunities(targetCommunities, NhincConstants.PATIENT_DISCOVERY_SERVICE_NAME);
            }
            catch (ConnectionManagerException ex)
            {
                log.error("Failed to obtain target URLs", ex);
            }
        }
        return urlInfoList;
    }

    protected PatientDiscovery201305Processor getPatientDiscovery201305Processor()
    {
        return new PatientDiscovery201305Processor();
    }

    protected NhincProxyPatientDiscoverySecuredImpl getNhincProxyPatientDiscoverySecuredImpl()
    {
        return new NhincProxyPatientDiscoverySecuredImpl();
    }

    protected RespondingGatewayPRPAIN201305UV02RequestType createNewRequest(RespondingGatewayPRPAIN201305UV02RequestType request, AssertionType assertion, CMUrlInfo urlInfo)
    {
        RespondingGatewayPRPAIN201305UV02RequestType newRequest = new RespondingGatewayPRPAIN201305UV02RequestType();
        PRPAIN201305UV02 new201305 = getPatientDiscovery201305Processor().createNewRequest(request.getPRPAIN201305UV02(), urlInfo.getHcid());

        newRequest.setAssertion(assertion);
        newRequest.setPRPAIN201305UV02(new201305);
        newRequest.setNhinTargetCommunities(request.getNhinTargetCommunities());
        return newRequest;
    }

    protected ResponseFactory getResponseFactory()
    {
        return new ResponseFactory();
    }

    protected PRPAIN201306UV02 sendToNhinProxy(RespondingGatewayPRPAIN201305UV02RequestType newRequest, AssertionType assertion, String url)
    {
        PRPAIN201306UV02 resultFromNhin = null;

        if(newRequest == null)
        {
            log.warn("RespondingGatewayPRPAIN201305UV02RequestType was null.");
        }
        else if(assertion == null)
        {
            log.warn("AssertionType was null.");
        }
        else if(url == null)
        {
            log.warn("URL was null.");
        }
        else
        {
            NhinTargetSystemType oTargetSystemType = new NhinTargetSystemType();
            oTargetSystemType.setUrl(url);

            //format request for nhincProxyPatientDiscoveryImpl call
            ProxyPRPAIN201305UVProxySecuredRequestType oProxyPRPAIN201305UVProxySecuredRequestType =
                    new ProxyPRPAIN201305UVProxySecuredRequestType();
            oProxyPRPAIN201305UVProxySecuredRequestType.setPRPAIN201305UV02(newRequest.getPRPAIN201305UV02());
            oProxyPRPAIN201305UVProxySecuredRequestType.setNhinTargetSystem(oTargetSystemType);

            resultFromNhin = getNhincProxyPatientDiscoverySecuredImpl().proxyPRPAIN201305UV(oProxyPRPAIN201305UVProxySecuredRequestType, assertion);


            //process the response
            ResponseParams params = new ResponseParams();
            params.assertion = assertion;
            params.origRequest = oProxyPRPAIN201305UVProxySecuredRequestType;
            params.response = resultFromNhin;

            try
            {
                resultFromNhin = getResponseFactory().getResponseMode().processResponse(params);
            }
            catch (Exception ex)
            {
                log.error("Error processing NHIN proxy response: " + ex.getMessage(), ex);
                resultFromNhin = new PRPAIN201306UV02();
            }
        }

        return resultFromNhin;
    }

    protected RespondingGatewayPRPAIN201306UV02ResponseType getResponseFromCommunities(RespondingGatewayPRPAIN201305UV02RequestType request, AssertionType assertion)
    {
        log.debug("Entering getResponseFromCommunities");
        RespondingGatewayPRPAIN201306UV02ResponseType response = null;

        CMUrlInfos urlInfoList = getEndpoints(request.getNhinTargetCommunities());

        //loop through the communities and send request if results were not null
        if((urlInfoList == null) || (urlInfoList.getUrlInfo().isEmpty()))
        {
            log.warn("No targets were found for the Patient Discovery Request");
        } 
        else
        {
            response = new RespondingGatewayPRPAIN201306UV02ResponseType();

            for (CMUrlInfo urlInfo : urlInfoList.getUrlInfo())
            {
                //create a new request to send out to each target community
                RespondingGatewayPRPAIN201305UV02RequestType newRequest = createNewRequest(request, assertion, urlInfo);

                //check the policy for the outgoing request to the target community
                boolean bIsPolicyOk = checkPolicy(newRequest, assertion);

                if (bIsPolicyOk)
                {
                    PRPAIN201306UV02 resultFromNhin = sendToNhinProxy(newRequest, assertion, urlInfo.getUrl());
                    // Store AA to HCID Mapping from response
                    getPatientDiscovery201306Processor().storeMapping(resultFromNhin);

                    CommunityPRPAIN201306UV02ResponseType communityResponse = new CommunityPRPAIN201306UV02ResponseType();
                    communityResponse.setPRPAIN201306UV02(resultFromNhin);

                    log.debug("Adding Community Response to response object");
                    response.getCommunityResponse().add(communityResponse);
                } //if (bIsPolicyOk)
                else
                {
                    log.error("The policy engine evaluated the request and denied the request.");
                } //else policy enging did not return a permit response
            } //for (NhinTargetCommunityType oTargetCommunity : request.getNhinTargetCommunities().getNhinTargetCommunity())
        }

        log.debug("Exiting getResponseFromCommunities");
        return response;
    }

    protected PatientDiscovery201306Processor getPatientDiscovery201306Processor()
    {
        return new PatientDiscovery201306Processor();
    }

    protected PatientDiscoveryAuditLogger getPatientDiscoveryAuditLogger()
    {
        return new PatientDiscoveryAuditLogger();
    }

    protected void logEntityPatientDiscoveryRequest(RespondingGatewayPRPAIN201305UV02RequestType request, AssertionType assertion)
    {
        getPatientDiscoveryAuditLogger().auditEntity201305(request, assertion, NhincConstants.AUDIT_LOG_INBOUND_DIRECTION);
    }

    protected void logAggregatedResponseFromNhin(RespondingGatewayPRPAIN201306UV02ResponseType response, AssertionType assertion)
    {
        getPatientDiscoveryAuditLogger().auditEntity201306(response, assertion, NhincConstants.AUDIT_LOG_OUTBOUND_DIRECTION);
    }

    protected PatientDiscoveryPolicyChecker getPatientDiscoveryPolicyChecker()
    {
        return new PatientDiscoveryPolicyChecker();
    }

    protected boolean checkPolicy(RespondingGatewayPRPAIN201305UV02RequestType request, AssertionType assertion)
    {
        if(request != null)
        {
            request.setAssertion(assertion);
        }
        return getPatientDiscoveryPolicyChecker().checkOutgoingPolicy(request);
    }

}
