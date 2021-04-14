package org.apache.dubbo.proxy.service;

import org.apache.dubbo.proxy.utils.ResultCode;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.registry.Registry;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.service.GenericService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class GenericInvoke {

    private static ApplicationConfig applicationConfig;
    private static volatile AtomicBoolean init = new AtomicBoolean(false);

    private static Registry registry;

    public static void setRegistry(Registry registry) {
        GenericInvoke.registry = registry;
    }

    private static void init() {
        RegistryConfig registryConfig = new RegistryConfig();
        registryConfig.setAddress(registry.getUrl().getProtocol() + "://" + registry.getUrl().getAddress());
        registryConfig.setGroup(registry.getUrl().getParameter(org.apache.dubbo.common.constants.CommonConstants.GROUP_KEY));
        applicationConfig = new ApplicationConfig();
        applicationConfig.setName("dubbo-proxy");
        applicationConfig.setRegistry(registryConfig);
    }

    private static ConcurrentHashMap<String, ReferenceConfig<GenericService>> cachedConfig = new ConcurrentHashMap<>();
    private static Logger logger = LoggerFactory.getLogger(GenericInvoke.class);

    public static Object genericCall(String interfaceName, String group,
                                     String version, String methodName, String[] paramTypes,
                                     Object[] paramObjs) {
        if (init.compareAndSet(false, true)) {
            init();
        }
        ReferenceConfig<GenericService> reference;
        // TODO 创建/获取当前接口对应的ReferenceConfig
        reference = addNewReference(interfaceName, group, version);

        try {
            // TODO 获取范化调用客户端
            GenericService svc = reference.get();
            logger.info("dubbo generic invoke, service is {}, method is {} , paramTypes is {} , paramObjs is {} , svc" +
                            " is {}.", interfaceName
                    , methodName,paramTypes,paramObjs,svc);
            // TODO 发起调用
            return svc.$invoke(methodName, paramTypes, paramObjs);
        } catch (Exception e) {
            logger.error("Generic invoke failed",e);
            if (e instanceof RpcException) {
                RpcException e1 = (RpcException)e;
                if (e1.isTimeout()) {
                    return ResultCode.TIMEOUT;
                }
                if (e1.isBiz()) {
                    return ResultCode.BIZERROR;
                }
                if (e1.isNetwork()) {
                    return ResultCode.NETWORKERROR;
                }
                if (e1.isSerialization()) {
                    return ResultCode.SERIALIZATION;
                }
            }
            throw e;
        }
    }

    private static ReferenceConfig<GenericService> addNewReference(String interfaceName,
                                                                   String group, String version) {
        ReferenceConfig<GenericService> reference;
        String cachedKey = interfaceName + group + version;
        reference = cachedConfig.get(cachedKey);
        if (reference == null) {
            ReferenceConfig<GenericService> newReference = initReference(interfaceName, group,
                    version);
            ReferenceConfig<GenericService> oldReference = cachedConfig.putIfAbsent(cachedKey, newReference);
            if (oldReference != null) {
                reference = oldReference;
            } else {
                reference = newReference;
            }
        }
        return reference;
    }

    private static ReferenceConfig<GenericService> initReference(String interfaceName, String group,
                                                                 String version) {
        ReferenceConfig<GenericService> reference = new ReferenceConfig<>();
        reference.setGeneric(true);
        reference.setApplication(applicationConfig);
        reference.setGroup(group);
        reference.setVersion(version);
        reference.setInterface(interfaceName);
        return reference;
    }
}
