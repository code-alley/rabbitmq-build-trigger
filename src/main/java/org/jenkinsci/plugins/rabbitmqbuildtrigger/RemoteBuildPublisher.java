/**
 *
 */
package org.jenkinsci.plugins.rabbitmqbuildtrigger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import jenkins.model.Configuration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.rabbitmqconsumer.extensions.MessageQueueListener;
import org.jenkinsci.plugins.rabbitmqconsumer.publishers.PublishChannel;
import org.jenkinsci.plugins.rabbitmqconsumer.publishers.PublishChannelFactory;
import org.jenkinsci.plugins.rabbitmqconsumer.publishers.PublishResult;
import org.kohsuke.stapler.DataBoundConstructor;

import com.rabbitmq.client.AMQP.BasicProperties;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.StreamBuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;

/**
 * The extension publish build result using rabbitmq.
 *
 * @author rinrinne a.k.a. rin_ne
 */
public class RemoteBuildPublisher extends Notifier {

    private static final Logger LOGGER = Logger.getLogger(RemoteBuildPublisher.class.getName());

    private static final String KEY_PROJECT = "project";
    private static final String KEY_NUMBER = "number";
    private static final String KEY_STATUS = "status";
    private static final String KEY_PARAMETERS = "parameters";
    private static final String HEADER_JENKINS_URL = "jenkins-url";
    private static final String JSON_CONTENT_TYPE = "application/json";

    private static final String LOG_HEADER = "Publish to RabbitMQ: ";

    private String brokerName;
    private String routingKey;

    /**
     * Creates instance with specified parameters.
     */
    @DataBoundConstructor
    public RemoteBuildPublisher(String brokerName, String routingKey) {
        this.brokerName = brokerName;
        if (StringUtils.isBlank(routingKey)) {
            this.routingKey = RemoteBuildPublisher.class.getPackage().getName();
        } else {
            this.routingKey = routingKey;
        }
    }

    /**
     * Gets broker name.
     *
     * @return the broker name.
     */
    public String getBrokerName() {
        return brokerName;
    }

    /**
     * Sets broker name.
     *
     * @param brokerName the broker name.
     */
    public void setBrokerName(String brokerName) {
        this.brokerName = brokerName;
    }

    /**
     * @inheritDoc
     */
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    /**
     * @inheritDoc
     */
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {

        if (brokerName == null || brokerName.length() == 0) {
            return true;
        }

        //LOGGER.info("### perform project name : " + build.getProject().getName());
        
        
        
        
        // Generate message (JSON format)
        JSONObject json = new JSONObject();
        json.put(KEY_PROJECT, build.getProject().getName());
        json.put(KEY_NUMBER, build.getNumber());
        json.put(KEY_STATUS, build.getResult().toString());

        try{
        	RemoteBuildListener rblistener = MessageQueueListener.all().get(RemoteBuildListener.class);
        	Set<RemoteBuildTrigger> triggers =  rblistener.getTrigger();

        	for(RemoteBuildTrigger trigger : triggers){
        		//LOGGER.info("### trigger project name : " + trigger.getProjectName());
        		//LOGGER.info("### trigger spec : " + trigger.getSpec());

        		if(build.getProject().getName().equalsIgnoreCase(trigger.getProjectName())){
        			if(trigger.getJsonParameters() != null){
        				//LOGGER.info("### trigger jsonParameters : " + trigger.getJsonParameters().toString());
        				json.put(KEY_PARAMETERS,  trigger.getJsonParameters());
        			}
        					
        		}

        	}


        }catch(Exception e){
        	e.printStackTrace();
        }
        
        // Basic property
        BasicProperties.Builder builder = new BasicProperties.Builder();
        builder.appId(RemoteBuildTrigger.PLUGIN_APPID);
        builder.contentType(JSON_CONTENT_TYPE);

        // Header
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(HEADER_JENKINS_URL, Jenkins.getInstance().getRootUrl());
        builder.headers(headers);

        // Publish message
        PublishChannel ch = PublishChannelFactory.getPublishChannel();
        if (ch != null && ch.isOpen()) {
            if (brokerName != null) {
                // return value is not needed if you don't need to wait.
                Future<PublishResult> future = ch.publish(brokerName, routingKey, builder.build(), json.toString().getBytes());

                // Wait until publish is completed.
                try {
                    PublishResult result = future.get();

                    if (result.isSuccess()) {
                        listener.getLogger().println(LOG_HEADER + "Success.");
                    } else {
                        listener.getLogger().println(LOG_HEADER + "Fail - " + result.getMessage());
                    }
                } catch (Exception e) {
                    LOGGER.warning(e.getMessage());
                    listener.getLogger().println(LOG_HEADER + "Fail due to exception.");
                }
            }
        }
        
        LOGGER.info("### perform end ############# ");
        return true;
    }

    /**
     * @inheritDoc
     */
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * The descriptor for this publisher.
     *
     * @author rinrinne a.k.a. rin_ne
     */
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        /**
         * @inheritDoc
         */
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> project) {
            return true;
        }

        /**
         * @inheritDoc
         */
        @Override
        public String getDisplayName() {
            return Messages.RabbitMQBuildPublisher();
        }
    }
}
