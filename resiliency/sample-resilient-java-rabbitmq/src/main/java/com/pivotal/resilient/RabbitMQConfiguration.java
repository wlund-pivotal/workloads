package com.pivotal.resilient;

import com.pivotal.resilient.amqp.AMQPConnectionProvider;
import com.pivotal.resilient.amqp.AMQPConnectionProviderImpl;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.ConnectionFactory;
import org.apache.tomcat.jni.SSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.Cloud;
import org.springframework.cloud.CloudFactory;
import org.springframework.cloud.service.common.AmqpServiceInfo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.TaskScheduler;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class RabbitMQConfiguration {

    private Logger logger = LoggerFactory.getLogger(RabbitMQConfiguration.class);

    @Autowired
    private TaskScheduler executor;

    @Autowired
    ConnectionFactory connectionFactory;

    @Bean
    public CloudFactory cloudFactory() {
        CloudFactory cloudFactory = new CloudFactory();
        return cloudFactory;
    }

    @Bean
    public Cloud cloud(CloudFactory factory) {
        return factory.getCloud();
    }

    @Bean
    public ConnectionFactory amqpConnectionFactory(Cloud cloud) throws NoSuchAlgorithmException, KeyManagementException, URISyntaxException, CertificateException, KeyStoreException, IOException, UnrecoverableKeyException {
        ConnectionFactory factory = new ConnectionFactory();

        //initConnectionFactoryWithSSLSupport(factory);
        initConnectionFactoryWithCustomSSLSupport(factory);
        initConnetionFactoryWithAMQPCredentials(cloud, factory);

        factory.setAutomaticRecoveryEnabled(true);
        factory.setTopologyRecoveryEnabled(false);
        factory.setConnectionTimeout(10000);
        factory.setNetworkRecoveryInterval(1000); // how long will automatic recovery wait before attempting to reconnect, in ms; default is 5000

        logger.info("Creating connection factory using username:{}, vhost:{}", factory.getUsername(), factory.getVirtualHost());

        return factory;
    }

    @Bean(destroyMethod = "shutdown", name = "consumer")
    public AMQPConnectionProvider consumer(Cloud cloud, ConnectionFactory factory, TaskScheduler scheduler) {
        logger.info("Creating AMQPConnectionProvider:consumer");
        AMQPConnectionProviderImpl provider =  new AMQPConnectionProviderImpl("consumer", factory, getAmqpAddressesFrom(cloud), scheduler);
        provider.start();
        return provider;
    }

    @Bean(destroyMethod = "shutdown")
    @Primary
    public AMQPConnectionProvider producer(Cloud cloud, ConnectionFactory factory, TaskScheduler scheduler) {
        logger.info("Creating AMQPConnectionProvider:producer");
        AMQPConnectionProviderImpl provider =  new AMQPConnectionProviderImpl("producer", factory, getAmqpAddressesFrom(cloud), scheduler);
        provider.start();
        return provider;
    }


    private void initConnetionFactoryWithAMQPCredentials(Cloud cloud, ConnectionFactory factory) throws NoSuchAlgorithmException, KeyManagementException, URISyntaxException {
        AmqpServiceInfo amqp = cloud.getSingletonServiceInfoByType(AmqpServiceInfo.class);
        factory.setUri(amqp.getUri());
    }
    private List<Address> getAmqpAddressesFrom(Cloud cloud) {
        AmqpServiceInfo amqp = cloud.getSingletonServiceInfoByType(AmqpServiceInfo.class);
        List<String> uris = new ArrayList<>();
        if (amqp.getUris() != null) {
            uris.addAll(amqp.getUris());
        }else {
            uris.add(amqp.getUri());
        }
        return uris.stream().map(uri -> {
            try {
                URI amqpURI = new URI(uri);
                return new Address(amqpURI.getHost(), amqpURI.getPort());
            } catch (URISyntaxException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());
    }

    private void initConnectionFactoryWithSSLSupport(ConnectionFactory connectionFactory) throws NoSuchAlgorithmException {
        SSLContext sslContext = SSLContext.getDefault();
        connectionFactory.useSslProtocol(sslContext);
        //connectionFactory.enableHostnameVerification();
    }
    private void initConnectionFactoryWithCustomSSLSupport(ConnectionFactory connectionFactory) throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {

        SSLContext sslContext = SSLContext.getInstance("TLS");
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init((KeyStore)null);
        sslContext.init((KeyManager[])null, trustManagerFactory.getTrustManagers(), (SecureRandom)null);
        connectionFactory.useSslProtocol(sslContext);

    }
}

