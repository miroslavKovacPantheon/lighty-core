/*
 * Copyright (c) 2018 Pantheon Technologies s.r.o. All Rights Reserved.
 *
 * This Source Code Form is subject to the terms of the lighty.io-core
 * Fair License 5, version 0.9.1. You may obtain a copy of the License
 * at: https://github.com/PantheonTechnologies/lighty-core/LICENSE.md
 */
package io.lighty.modules.southbound.netconf.impl.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import io.lighty.core.controller.api.LightyServices;
import io.lighty.core.controller.impl.config.ConfigurationException;
import io.lighty.modules.southbound.netconf.impl.config.NetconfConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.aaa.encrypt.AAAEncryptionServiceImpl;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.client.NetconfClientDispatcherImpl;
import org.opendaylight.yang.gen.v1.config.aaa.authn.encrypt.service.config.rev160915.AaaEncryptServiceConfig;
import org.opendaylight.yang.gen.v1.config.aaa.authn.encrypt.service.config.rev160915.AaaEncryptServiceConfigBuilder;
import org.opendaylight.yangtools.yang.binding.YangModuleInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfConfigUtils {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfConfigUtils.class);
    public static final String NETCONF_CONFIG_ROOT_ELEMENT_NAME = "netconf";

    public static final Set<YangModuleInfo> NETCONF_TOPOLOGY_MODELS = ImmutableSet.of(
            org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.$YangModuleInfoImpl.getInstance(),
            org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.$YangModuleInfoImpl.getInstance(),

            org.opendaylight.yang.gen.v1.urn.opendaylight.yang.extension.yang.ext.rev130709.$YangModuleInfoImpl.getInstance(),
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.$YangModuleInfoImpl.getInstance(),
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.$YangModuleInfoImpl.getInstance(),
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.extension.rev131210.$YangModuleInfoImpl.getInstance(),
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160409.$YangModuleInfoImpl.getInstance()
    );

    public static final Set<YangModuleInfo> NETCONF_CALLHOME_MODELS = ImmutableSet.of(
            org.opendaylight.yang.gen.v1.urn.opendaylight.callhome.device.status.rev170112.$YangModuleInfoImpl.getInstance(),
            org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev161109.$YangModuleInfoImpl.getInstance()
    );

    /**
     * Load netconf southbound configuration from InputStream containing JSON data.
     * Lighty services are not populated in this configuration.
     * @param jsonConfigInputStream InputStream containing Netconf config. data in JSON format.
     * @return Object representation of configuration data.
     * @throws ConfigurationException In case JSON configuration cannot be deserializable to JSON
     * tree nodes or cannot bind JSON tree node to type.
     */
    public static NetconfConfiguration createNetconfConfiguration(
            final InputStream jsonConfigInputStream) throws ConfigurationException {
        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode configNode;
        try {
            configNode = mapper.readTree(jsonConfigInputStream);
        } catch (IOException e) {
            throw new ConfigurationException("Cannot deserialize Json content to Json tree nodes",
                    e);
        }
        if (!configNode.has(NETCONF_CONFIG_ROOT_ELEMENT_NAME)) {
            LOG.warn("Json config does not contain {} element. Using defaults.",
                    NETCONF_CONFIG_ROOT_ELEMENT_NAME);
            return createDefaultNetconfConfiguration();
        }
        final JsonNode netconfNode = configNode.path(NETCONF_CONFIG_ROOT_ELEMENT_NAME);

        final NetconfConfiguration netconfConfiguration;
        try {
            netconfConfiguration = mapper.treeToValue(netconfNode, NetconfConfiguration.class);
        } catch (JsonProcessingException e) {
            throw new ConfigurationException(
                    String.format("Cannot bind Json tree to type: %s", NetconfConfiguration.class),
                    e);
        }
        return netconfConfiguration;
    }

    /**
     * Create default Netconf Southbound configuration, Lighty services are not populated.
     * @return Object representation of configuration data.
     */
    public static NetconfConfiguration createDefaultNetconfConfiguration() {
        return new NetconfConfiguration();
    }

    /**
     * Inject services from LightyServices to Netconf southbound configuration.
     * @param configuration Netconf southbound configuration where should be services injected.
     * @param lightyServices LightyServices from running Lighty core.
     * @return Netconf southbound configuration with injected services from Lighty core.
     */
    public static NetconfConfiguration injectServicesToConfig(
            final NetconfConfiguration configuration, final LightyServices lightyServices) {
        final AaaEncryptServiceConfig aaaConfig =
                new AaaEncryptServiceConfigBuilder().setEncryptKey("V1S1ED4OMeEh")
                        .setPasswordLength(12).setEncryptSalt("TdtWeHbch/7xP52/rp3Usw==")
                        .setEncryptMethod("PBKDF2WithHmacSHA1").setEncryptType("AES")
                        .setEncryptIterationCount(32768).setEncryptKeyLength(128)
                        .setCipherTransforms("AES/CBC/PKCS5Padding").build();
        final AAAEncryptionService aaa =
                new AAAEncryptionServiceImpl(aaaConfig, lightyServices.getBindingDataBroker());
        configuration.setAaaService(aaa);
        return configuration;
    }

    /**
     * Inject services from LightyServices and netconf client dispatcher to Netconf southbound topology configuration.
     * @param configuration Netconf southbound topology configuration where should be services injected.
     * @param lightyServices LightyServices from running Lighty core.
     * @return Netconf southbound topology configuration with injected services from Lighty core.
     */
    public static NetconfConfiguration injectServicesToTopologyConfig(
            final NetconfConfiguration configuration, final LightyServices lightyServices) {
        injectServicesToConfig(configuration, lightyServices);
        injectClient(lightyServices, configuration);
        return configuration;
    }

    /**
     * Inject netconf client dispatcher to Netconf southbound configuration, it uses {@link LightyServices}.
     * @param services LightyServices from running Lighty core.
     * @param configuration Netconf southbound configuration where should be services injected.
     * @return Netconf southbound configuration with injected services from Lighty core.
     */
    private static NetconfConfiguration injectClient(final LightyServices services,
            final NetconfConfiguration configuration) {
        final NetconfClientDispatcher client =
                new NetconfClientDispatcherImpl(services.getBossGroup(), services.getWorkerGroup(),
                        services.getTimer());
        configuration.setClientDispatcher(client);
        return configuration;
    }

}
