//
// A Structured Logger for Fluent
//
// Copyright (C) 2011 - 2013 OZAWA Tsuyoshi
//
//    Licensed under the Apache License, Version 2.0 (the "License");
//    you may not use this file except in compliance with the License.
//    You may obtain a copy of the License at
//
//        http://www.apache.org/licenses/LICENSE-2.0
//
//    Unless required by applicable law or agreed to in writing, software
//    distributed under the License is distributed on an "AS IS" BASIS,
//    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//    See the License for the specific language governing permissions and
//    limitations under the License.
//
package org.fluentd.logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Properties;
import java.util.WeakHashMap;

import org.fluentd.logger.sender.ExponentialDelayReconnector;
import org.fluentd.logger.sender.RawSocketSender;
import org.fluentd.logger.sender.Reconnector;
import org.fluentd.logger.sender.Sender;

public class FluentLoggerFactory {

    private final Map<FluentLogger, String> loggers;

    public FluentLoggerFactory() {
        loggers = new WeakHashMap<FluentLogger, String>();
    }

    public FluentLogger getLogger(String tagPrefix) {
        return getLogger(tagPrefix, "localhost", 24224);
    }

    public FluentLogger getLogger(String tagPrefix, String host, int port) {
        return getLogger(tagPrefix, host, port, 3 * 1000, 1 * 1024 * 1024, false);
    }

    public FluentLogger getLogger(String tagPrefix, String host, int port, int timeout, int bufferCapacity) {
        return getLogger(tagPrefix, host, port, timeout, bufferCapacity, false);
    }

    public FluentLogger getLogger(String tagPrefix, String host, int port, int timeout, int bufferCapacity,
            boolean keepAlive) {
        return getLogger(tagPrefix, host, port, timeout, bufferCapacity, keepAlive, new ExponentialDelayReconnector());
    }

    public synchronized FluentLogger getLogger(String tagPrefix, String host, int port, int timeout, int bufferCapacity,
            boolean keepAlive, Reconnector reconnector) {
        String key = String.format("%s_%s_%d_%d_%d_%b", new Object[] { tagPrefix, host, port, timeout, bufferCapacity, keepAlive });

        for (Map.Entry<FluentLogger, String> entry : loggers.entrySet()) {
            if (entry.getValue().equals(key)) {
                FluentLogger found = entry.getKey();
                if(found != null) {
                    return found;
                }
                break;
            }
        }

        Sender sender = null;
        Properties props = System.getProperties();
        if (!props.containsKey(Config.FLUENT_SENDER_CLASS)) {
            // create default sender object
            sender = new RawSocketSender(host, port, timeout, bufferCapacity, keepAlive, reconnector);
        } else {
            String senderClassName = props.getProperty(Config.FLUENT_SENDER_CLASS);
            try {
                sender = createSenderInstance(senderClassName, new Object[] { host, port, timeout, bufferCapacity });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        FluentLogger logger = new FluentLogger(tagPrefix, sender);
        loggers.put(logger, key);
        return logger;
    }

    @SuppressWarnings("unchecked")
    private Sender createSenderInstance(final String className, final Object[] params) throws ClassNotFoundException,
            SecurityException, NoSuchMethodException, IllegalArgumentException, InstantiationException,
            IllegalAccessException, InvocationTargetException {
        Class<Sender> cl = (Class<Sender>) FluentLogger.class.getClassLoader().loadClass(className);
        Constructor<Sender> cons = cl.getDeclaredConstructor(new Class[] { String.class, int.class, int.class,
                int.class });
        return (Sender) cons.newInstance(params);
    }

    /**
     * the method is for testing
     */
    Map<FluentLogger, String> getLoggers() {
        return loggers;
    }

    public synchronized void closeAll() {
        for (FluentLogger logger : loggers.keySet()) {
            logger.close();
        }
        loggers.clear();
    }

    public synchronized void flushAll() {
        for (FluentLogger logger : loggers.keySet()) {
            logger.flush();
        }
    }

}
