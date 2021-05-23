package com.example.demo;

import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.GenericObjectPool;

import javax.mail.Transport;

class TransportPool extends GenericObjectPool<Transport> {


    public TransportPool(PooledObjectFactory<Transport> factory) {
        super(factory);
    }
}
