/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.cluster.protocol.impl;

import java.io.IOException;
import java.util.Collection;

import org.apache.nifi.cluster.protocol.NodeProtocolSender;
import org.apache.nifi.cluster.protocol.ProtocolException;
import org.apache.nifi.cluster.protocol.ProtocolHandler;
import org.apache.nifi.cluster.protocol.ProtocolListener;
import org.apache.nifi.cluster.protocol.UnknownServiceAddressException;
import org.apache.nifi.cluster.protocol.message.NodeBulletinsMessage;
import org.apache.nifi.cluster.protocol.message.ConnectionRequestMessage;
import org.apache.nifi.cluster.protocol.message.ConnectionResponseMessage;
import org.apache.nifi.cluster.protocol.message.ControllerStartupFailureMessage;
import org.apache.nifi.cluster.protocol.message.HeartbeatMessage;
import org.apache.nifi.cluster.protocol.message.ReconnectionFailureMessage;
import org.apache.nifi.reporting.BulletinRepository;

public class NodeProtocolSenderListener implements NodeProtocolSender, ProtocolListener {

    private final NodeProtocolSender sender;
    private final ProtocolListener listener;

    public NodeProtocolSenderListener(final NodeProtocolSender sender, final ProtocolListener listener) {
        if (sender == null) {
            throw new IllegalArgumentException("NodeProtocolSender may not be null.");
        } else if (listener == null) {
            throw new IllegalArgumentException("ProtocolListener may not be null.");
        }
        this.sender = sender;
        this.listener = listener;
    }

    @Override
    public void stop() throws IOException {
        if (!isRunning()) {
            throw new IllegalStateException("Instance is already stopped.");
        }
        listener.stop();
    }

    @Override
    public void start() throws IOException {
        if (isRunning()) {
            throw new IllegalStateException("Instance is already started.");
        }
        listener.start();
    }

    @Override
    public boolean isRunning() {
        return listener.isRunning();
    }

    @Override
    public boolean removeHandler(final ProtocolHandler handler) {
        return listener.removeHandler(handler);
    }

    @Override
    public Collection<ProtocolHandler> getHandlers() {
        return listener.getHandlers();
    }

    @Override
    public void addHandler(final ProtocolHandler handler) {
        listener.addHandler(handler);
    }

    @Override
    public void heartbeat(final HeartbeatMessage msg) throws ProtocolException, UnknownServiceAddressException {
        sender.heartbeat(msg);
    }

    @Override
    public ConnectionResponseMessage requestConnection(final ConnectionRequestMessage msg) throws ProtocolException, UnknownServiceAddressException {
        return sender.requestConnection(msg);
    }

    @Override
    public void notifyControllerStartupFailure(final ControllerStartupFailureMessage msg) throws ProtocolException, UnknownServiceAddressException {
        sender.notifyControllerStartupFailure(msg);
    }

    @Override
    public void notifyReconnectionFailure(final ReconnectionFailureMessage msg) throws ProtocolException, UnknownServiceAddressException {
        sender.notifyReconnectionFailure(msg);
    }

    @Override
    public void sendBulletins(NodeBulletinsMessage msg) throws ProtocolException, UnknownServiceAddressException {
        sender.sendBulletins(msg);
    }

    @Override
    public void setBulletinRepository(final BulletinRepository bulletinRepository) {
        listener.setBulletinRepository(bulletinRepository);
    }
}
