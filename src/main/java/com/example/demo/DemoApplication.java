package com.example.demo;

import org.apache.commons.pool2.*;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.lang.Nullable;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import javax.mail.*;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

@SpringBootApplication
public class DemoApplication {
    private static final Logger LOGGER = LoggerFactory.getLogger(DemoApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }


    static class TransportFactory extends BasePooledObjectFactory<Transport> {
        private static final String DEFAULT_PROTOCOL = "smtp";

        private Session session;
        private Properties javaMailProperties;
        private String protocol;
        private String username;
        private String password;
        private String host;
        private int port;

        TransportFactory(Properties javaMailProperties, String protocol, String host, int port) {
            this.javaMailProperties = javaMailProperties;
            this.protocol = protocol;
            this.host = host;
            this.port = port;
        }

        private synchronized Session getSession() {
            if (this.session == null) {
                this.session = Session.getInstance(this.javaMailProperties);
            }
            return this.session;
        }

        private Transport getTransport(Session session) throws NoSuchProviderException {
            String protocol = this.protocol;
            if (protocol == null) {
                protocol = session.getProperty("mail.transport.protocol");
                if (protocol == null) {
                    protocol = DEFAULT_PROTOCOL;
                }
            }
            return session.getTransport(protocol);
        }

        protected Transport connectTransport() throws MessagingException {
            if ("".equals(username)) {  // probably from a placeholder
                username = null;
                if ("".equals(password)) {  // in conjunction with "" username, this means no password to use
                    password = null;
                }
            }

            Transport transport = getTransport(getSession());
            LOGGER.debug("Connecting Transport " + transport.toString());
            transport.connect(host, port, username, password);
            return transport;
        }

        @Override
        public Transport create() throws Exception {
            return connectTransport();
        }

        @Override
        public PooledObject<Transport> wrap(Transport obj) {
            return new DefaultPooledObject<>(obj);
        }

        @Override
        public void destroyObject(PooledObject<Transport> p) throws Exception {
            System.out.println("DESTROYING " + p.toString() + " with state " + p.getState());
            p.getObject().close();
        }
    }

    static class TransportPool extends GenericObjectPool<Transport> {


        public TransportPool(PooledObjectFactory<Transport> factory) {
            super(factory);
        }
    }

    static class PooledJavaMailSender extends JavaMailSenderImpl {
        private static final String HEADER_MESSAGE_ID = "Message-ID";
        private ObjectPool<Transport> transportPool;

        PooledJavaMailSender(ObjectPool<Transport> transportPool) {
            this.transportPool = transportPool;
        }

        protected void doSend(MimeMessage[] mimeMessages, @Nullable Object[] originalMessages) throws MailException {
            Map<Object, Exception> failedMessages = new LinkedHashMap<>();
            Transport transport = null;

            try {
                transport = transportPool.borrowObject();
                for (int i = 0; i < mimeMessages.length; i++) {
                    // Send message via current transport...
                    MimeMessage mimeMessage = mimeMessages[i];
                    try {
                        if (mimeMessage.getSentDate() == null) {
                            mimeMessage.setSentDate(new Date());
                        }
                        String messageId = mimeMessage.getMessageID();
                        mimeMessage.saveChanges();
                        if (messageId != null) {
                            // Preserve explicitly specified message id...
                            mimeMessage.setHeader(HEADER_MESSAGE_ID, messageId);
                        }
                        Address[] addresses = mimeMessage.getAllRecipients();
                        transport.sendMessage(mimeMessage, (addresses != null ? addresses : new Address[0]));
                    } catch (Exception ex) {
                        Object original = (originalMessages != null ? originalMessages[i] : mimeMessage);
                        failedMessages.put(original, ex);
                    }
                }
            } catch (Exception ex) {
                throw new MailSendException("Failed to get transport from pool", ex);
            } finally {
                try {
                    if (transport != null) {
                        transportPool.returnObject(transport);
                    }
                } catch (Exception ex) {
                    if (!failedMessages.isEmpty()) {
                        throw new MailSendException("Failed to close server connection after message failures", ex,
                                failedMessages);
                    } else {
                        throw new MailSendException("Failed to close server connection after message sending", ex);
                    }
                }
            }

            if (!failedMessages.isEmpty()) {
                throw new MailSendException(failedMessages);
            }
        }
    }

    static class EmailAddress {
        private InternetAddress address;

        public EmailAddress(String address) {
            try {
                this.address = new InternetAddress(address, true);
            } catch (AddressException e) {
                e.printStackTrace();
            }
        }
    }

    @Bean
    TransportFactory transportFactory() {
        Properties props = new Properties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.debug", "false");
        return new TransportFactory(props, "smtp", "127.0.0.1", 3025);
    }

    @Bean
    TransportPool transportPool(TransportFactory factory) {
        return new TransportPool(factory);
    }

    @Bean
    public JavaMailSender javaMailSender(TransportPool pool) {
        return new PooledJavaMailSender(pool);
    }

}
