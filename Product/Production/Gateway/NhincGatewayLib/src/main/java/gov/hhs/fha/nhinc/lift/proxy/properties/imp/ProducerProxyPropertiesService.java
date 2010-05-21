//
// Non-Export Controlled Information
//
//####################################################################
//## The MIT License
//##
//## Copyright (c) 2010 Harris Corporation
//##
//## Permission is hereby granted, free of charge, to any person
//## obtaining a copy of this software and associated documentation
//## files (the "Software"), to deal in the Software without
//## restriction, including without limitation the rights to use,
//## copy, modify, merge, publish, distribute, sublicense, and/or sell
//## copies of the Software, and to permit persons to whom the
//## Software is furnished to do so, subject to the following conditions:
//##
//## The above copyright notice and this permission notice shall be
//## included in all copies or substantial portions of the Software.
//##
//## THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
//## EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
//## OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
//## NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
//## HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
//## WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
//## FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
//## OTHER DEALINGS IN THE SOFTWARE.
//##
//####################################################################
//********************************************************************
// FILE: ProducerProxyPropertiesService.java
//
// Copyright (C) 2010 Harris Corporation. All rights reserved.
//
// CLASSIFICATION: Unclassified
//
// DESCRIPTION: ProducerProxyPropertiesService.java
//
//
// LIMITATIONS: None
//
// SOFTWARE HISTORY:
//
//> Feb 24, 2010 PTR#  - R. Robinson
// Initial Coding.
//<
//********************************************************************
package gov.hhs.fha.nhinc.lift.proxy.properties.imp;

import gov.hhs.fha.nhinc.lift.common.util.LiftConnectionRequestToken;
import gov.hhs.fha.nhinc.properties.PropertyAccessException;
import java.io.IOException;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

import gov.hhs.fha.nhinc.lift.proxy.properties.interfaces.ProducerProxyPropertiesFacade;
import gov.hhs.fha.nhinc.nhinclib.NhincConstants;
import gov.hhs.fha.nhinc.properties.PropertyAccessor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ProducerProxyPropertiesService implements
        ProducerProxyPropertiesFacade {

    private Log log = null;

    public ProducerProxyPropertiesService() {
        log = createLogger();
    }

    protected Log createLogger() {
        return ((log != null) ? log : LogFactory.getLog(getClass()));
    }

    @Override
    public boolean verifySecurityForRequest(LiftConnectionRequestToken request) {

        System.out.println("Need to integrate in new table - ProducerProxyPropertiesService.verifySecurityForRequest");
        System.out.println("For now returning TRUE - match found.");
        return true;
    }

    @Override
    public Socket getSocketToServerForRequest(LiftConnectionRequestToken request)
            throws IOException {

        Socket socket = new Socket();
        try {
            String fileServerIP = PropertyAccessor.getProperty(NhincConstants.GATEWAY_PROPERTY_FILE, NhincConstants.LIFT_FILESERVER_IP);
            String fileServerPort = PropertyAccessor.getProperty(NhincConstants.GATEWAY_PROPERTY_FILE, NhincConstants.LIFT_FILESERVER_PORT);

            int portNum = Integer.parseInt(fileServerPort);

            SocketAddress socketAddr = new InetSocketAddress(fileServerIP, portNum);

            socket.connect(socketAddr);
        } catch (PropertyAccessException ex) {
            log.error(ex.getMessage());
        }
        return socket;
    }

    @Override
    public void setKeyStoreProperty() {
        try {
            String loc = PropertyAccessor.getProperty(NhincConstants.GATEWAY_PROPERTY_FILE, NhincConstants.LIFT_KEYSTORE);
            String pass = PropertyAccessor.getProperty(NhincConstants.GATEWAY_PROPERTY_FILE, NhincConstants.LIFT_KEYSTOREPASS);
            System.setProperty("javax.net.ssl.keyStore", loc);
            System.setProperty("javax.net.ssl.keyStorePassword", pass);
        } catch (PropertyAccessException ex) {
            log.error(ex.getMessage());
        }
    }
}