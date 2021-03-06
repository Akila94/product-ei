/*
*  Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.wso2.carbon.esb.jms.inbound.transport.test;


import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.util.AXIOMUtil;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.engine.context.AutomationContext;
import org.wso2.carbon.automation.engine.context.TestUserMode;
import org.wso2.carbon.automation.extensions.servers.jmsserver.client.JMSQueueMessageProducer;
import org.wso2.carbon.automation.extensions.servers.jmsserver.controller.config.JMSBrokerConfigurationProvider;
import org.wso2.carbon.integration.common.admin.client.CarbonAppUploaderClient;
import org.wso2.carbon.integration.common.admin.client.LogViewerClient;
import org.wso2.carbon.logging.view.stub.types.carbon.LogEvent;
import org.wso2.esb.integration.common.clients.inbound.endpoint.InboundAdminClient;
import org.wso2.esb.integration.common.utils.ESBIntegrationTest;
import org.wso2.esb.integration.common.utils.JMSEndpointManager;
import org.wso2.esb.integration.common.utils.Utils;
import org.wso2.esb.integration.common.utils.common.ServerConfigurationManager;
import org.wso2.esb.integration.common.utils.servers.ActiveMQServer;

import javax.activation.DataHandler;
import java.io.File;
import java.net.URL;

/**
 * class tests consuming message from a queue using inbound endpoints
 */
public class JMSInboundMessagePollingTestCase extends ESBIntegrationTest{
	private LogViewerClient logViewerClient = null;
	private ServerConfigurationManager serverConfigurationManager;
	private InboundAdminClient inboundAdminClient;
	private ActiveMQServer activeMQServer = new ActiveMQServer();


	@BeforeClass(alwaysRun = true)
	protected void init() throws Exception {
		activeMQServer.startJMSBroker();
		super.init();
		serverConfigurationManager =
				new ServerConfigurationManager(new AutomationContext("ESB", TestUserMode.SUPER_TENANT_ADMIN));
		OMElement synapse =
				esbUtils.loadResource("/artifacts/ESB/jms/inbound/transport/jms_transport_proxy_service.xml");
		updateESBConfiguration(JMSEndpointManager.setConfigurations(synapse));
		inboundAdminClient = new InboundAdminClient(context.getContextUrls().getBackEndUrl(),getSessionCookie());
		logViewerClient = new LogViewerClient(contextUrls.getBackEndUrl(), getSessionCookie());
	}

    //This was disabled since already existing messages are not consumed intermittently when inbound is deployed
    //Should be checked in detail and fix in functionality
	@Test(groups = { "wso2.esb" }, description = "Polling Message from a Queue", enabled = false)
	public void testPollingMessages() throws Exception {
		JMSQueueMessageProducer sender =
				new JMSQueueMessageProducer(JMSBrokerConfigurationProvider.getInstance().getBrokerConfiguration());
		String queueName = "localq";

		try {
			sender.connect(queueName);
			for (int i = 0; i < 3; i++) {
				sender.pushMessage("<?xml version='1.0' encoding='UTF-8'?>" +
				                   "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\"" +
				                   " xmlns:ser=\"http://services.samples\" xmlns:xsd=\"http://services.samples/xsd\">" +
				                   "   <soapenv:Header/>" +
				                   "   <soapenv:Body>" +
				                   "      <ser:placeOrder>" +
				                   "         <ser:order>" +
				                   "            <xsd:price>100</xsd:price>" +
				                   "            <xsd:quantity>2000</xsd:quantity>" +
				                   "            <xsd:symbol>WSO2</xsd:symbol>" +
				                   "         </ser:order>" +
				                   "      </ser:placeOrder>" +
				                   "   </soapenv:Body>" +
				                   "</soapenv:Envelope>");
                log.info("Message " + i + " pushed to the JMS Queue");
			}
		} finally {
			sender.disconnect();
		}
        Thread.sleep(5000);

		addInboundEndpoint(addEndpoint1());
		boolean status = Utils.checkForLog(logViewerClient, "<xsd:symbol>WSO2</xsd:symbol>", 30);
		Assert.assertTrue(status, "Couldn't Consume messages from Queue");
	}

	@AfterClass(alwaysRun = true)
	public void destroy() throws Exception {
		super.cleanup();
		activeMQServer.stopJMSBroker();
	}

	/**
	 * Check whether the JMS listner has started before complete deployment of the JMS proxy.
	 * @throws Exception
	 */
	@Test(groups = {"wso2.esb"}, description = "Test JMS proxy deployment.")
	public void testDeploymentOrder() throws Exception {

		String message = "<?xml version='1.0' encoding='UTF-8'?>" +
				"<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\"" +
				" xmlns:ser=\"http://services.samples\" xmlns:xsd=\"http://services.samples/xsd\">" +
				"   <soapenv:Header/>" +
				"   <soapenv:Body>" +
				"      <ser:placeOrder>" +
				"         <ser:order>" +
				"            <xsd:price>100</xsd:price>" +
				"            <xsd:quantity>2000</xsd:quantity>" +
				"            <xsd:symbol>JMSTransport</xsd:symbol>" +
				"         </ser:order>" +
				"      </ser:placeOrder>" +
				"   </soapenv:Body>" +
				"</soapenv:Envelope>";

		JMSQueueMessageProducer sender = new JMSQueueMessageProducer(JMSBrokerConfigurationProvider.getInstance().getBrokerConfiguration());
		String queueName = "TestQueue";
		try {
			sender.connect(queueName);
			for (int i = 0; i < 5; i++) {
				sender.pushMessage(message);
			}
		} finally {
			sender.disconnect();
		}

		Thread.sleep(5000);

		LogViewerClient logViewerClient = new LogViewerClient(contextUrls.getBackEndUrl(), getSessionCookie());
		String errorMessage = "MessageConsumedBeforeDeployment";

		logViewerClient.clearLogs();
		//upload CAPP
		CarbonAppUploaderClient carbonAppUploaderClient =
				new CarbonAppUploaderClient(context.getContextUrls().getBackEndUrl(), sessionCookie);
		String carFileName = "JMSProxyDeploymentTestCar_1.0.0.car";
		carbonAppUploaderClient.uploadCarbonAppArtifact(carFileName,
				new DataHandler(new URL("file:" + File.separator + File.separator + getESBResourceLocation()
						+ File.separator + "car" + File.separator + carFileName)));

		boolean errorOccurred = Utils.checkForLog(logViewerClient, errorMessage,30);

		Assert.assertFalse(errorOccurred, "JMS listener started consuming messages before the proxy deployment");

	}


	private OMElement addEndpoint1() throws Exception {
		OMElement synapseConfig = null;
		synapseConfig = AXIOMUtil
				.stringToOM("<inboundEndpoint xmlns=\"http://ws.apache.org/ns/synapse\"\n" +
				            "                 name=\"TestJMS\"\n" +
				            "                 sequence=\"requestHandlerSeq\"\n" +
				            "                 onError=\"inFault\"\n" +
				            "                 protocol=\"jms\"\n" +
				            "                 suspend=\"false\">\n" +
				            "    <parameters>\n" +
				            "        <parameter name=\"interval\">10000</parameter>\n" +
				            "        <parameter name=\"transport.jms.Destination\">localq</parameter>\n" +
				            "        <parameter name=\"transport.jms.CacheLevel\">1</parameter>\n" +
				            "        <parameter name=\"transport.jms" +
				            ".ConnectionFactoryJNDIName\">QueueConnectionFactory</parameter>\n" +
				            "        <parameter name=\"java.naming.factory.initial\">org.apache.activemq.jndi.ActiveMQInitialContextFactory</parameter>\n" +
				            "        <parameter name=\"java.naming.provider.url\">tcp://localhost:61616</parameter>\n" +
				            "        <parameter name=\"transport.jms.SessionAcknowledgement\">AUTO_ACKNOWLEDGE</parameter>\n" +
				            "        <parameter name=\"transport.jms.SessionTransacted\">false</parameter>\n" +
				            "        <parameter name=\"transport.jms.ConnectionFactoryType\">queue</parameter>\n" +
				            "    </parameters>\n" +
				            "</inboundEndpoint>");

		return synapseConfig;
	}

}
