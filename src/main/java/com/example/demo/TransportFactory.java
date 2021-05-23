package com.example.demo;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Transport;
import java.util.Properties;

class TransportFactory extends BasePooledObjectFactory<Transport> {
    private static final Logger LOGGER = LoggerFactory.getLogger(TransportFactory.class);

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
    public boolean validateObject(PooledObject<Transport> p) {
        return p.getObject().isConnected();
    }

    @Override
    public void destroyObject(PooledObject<Transport> p) throws Exception {
        System.out.println("DESTROYING " + p.toString() + " with state " + p.getState());
        LOGGER.debug("DESTROYING " + p.toString() + " with state " + p.getState());
        p.getObject().close();
    }
}
