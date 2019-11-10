package io.joyrpc.config;

/*-
 * #%L
 * joyrpc
 * %%
 * Copyright (C) 2019 joyrpc.io
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import io.joyrpc.constants.Constants;
import io.joyrpc.constants.ExceptionCode;
import io.joyrpc.exception.InitializationException;
import io.joyrpc.invoker.GroupInvoker;
import io.joyrpc.proxy.ConsumerInvokeHandler;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static io.joyrpc.Plugin.GROUP_ROUTE;
import static io.joyrpc.constants.Constants.FROM_GROUP_OPTION;

/**
 * 消费组配置
 */
public class ConsumerGroupConfig<T> extends AbstractConsumerConfig<T> implements Serializable {

    /**
     * 目标参数（机房/分组）索引，第一个参数从0开始
     */
    protected Integer dstParam;
    /**
     * alias自适应，当没有分组时，可以自动增加
     */
    protected boolean aliasAdaptive;
    /**
     * 分组路由插件配置
     */
    protected String groupRouter;
    /**
     * 分组调用
     */
    protected transient GroupInvoker route;

    public Integer getDstParam() {
        return dstParam;
    }

    public void setDstParam(Integer dstParam) {
        this.dstParam = dstParam;
    }

    public boolean isAliasAdaptive() {
        return aliasAdaptive;
    }

    public void setAliasAdaptive(boolean aliasAdaptive) {
        this.aliasAdaptive = aliasAdaptive;
    }

    public String getGroupRouter() {
        return groupRouter;
    }

    public void setGroupRouter(String groupRouter) {
        this.groupRouter = groupRouter;
    }

    @Override
    protected Map<String, String> addAttribute2Map(final Map<String, String> params) {
        super.addAttribute2Map(params);
        addElement2Map(params, Constants.DST_PARAM_OPTION, dstParam);
        addElement2Map(params, Constants.ALIAS_ADAPTIVE_OPTION, aliasAdaptive);
        return params;
    }

    @Override
    protected void validateAlias() {
        if (alias == null || alias.isEmpty()) {
            throw new InitializationException("Value of \"alias\" is not specified in consumer" +
                    " config with key " + name() + " !", ExceptionCode.CONSUMER_ALIAS_IS_NULL);
        }
        //比普通的alias多允许逗号
        checkNormalWithCommaColon("alias", alias);
        //检查路由插件配置
        checkExtension(GROUP_ROUTE, GroupInvoker.class, "groupRouter", groupRouter);
    }

    @Override
    protected void doRefer(final CompletableFuture<Void> future) {
        //创建分组调用
        Class<T> proxyClass;
        try {
            proxyClass = getProxyClass();
        } catch (Exception e) {
            future.completeExceptionally(e);
            return;
        }
        route = GROUP_ROUTE.get(groupRouter, Constants.GROUP_ROUTER_OPTION.getValue());
        route.setAliasAdaptive(aliasAdaptive);
        route.setUrl(serviceUrl);
        route.setAlias(alias);
        route.setClass(proxyClass);
        route.setClassName(interfaceClazz);
        route.setConfigFunction(this::createGroupConfig);
        route.setup();
        //创建桩
        invokeHandler = new ConsumerInvokeHandler(route, interfaceClass);
        proxy();
        //创建消费者
        route.refer().whenComplete((v, t) -> {
            if (t == null) {
                future.complete(null);
            } else {
                future.completeExceptionally(t);
            }
        });
    }

    /**
     * 创建分组配置
     *
     * @param alias
     * @return
     */
    protected ConsumerConfig createGroupConfig(final String alias) {
        ConsumerConfig customConfig = new ConsumerConfig<>(this, alias);
        // 注册的时候标记属于分组调用的group
        customConfig.setParameter(FROM_GROUP_OPTION.getName(), "true");
        return customConfig;
    }

    @Override
    protected void doUnRefer(final CompletableFuture<Void> future) {
        if (invokeHandler == null) {
            future.complete(null);
        }
        invokeHandler = null;
        route.close().whenComplete((v, t) -> {
            if (t == null) {
                future.complete(null);
            } else {
                future.completeExceptionally(t);
            }
        });
    }

}
