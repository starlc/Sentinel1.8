/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel.demo.cluster.app;

import com.alibaba.csp.sentinel.demo.cluster.init.DemoClusterInitFuncByZk;
import com.alibaba.csp.sentinel.init.InitExecutor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author Eric Zhao
 */
@SpringBootApplication
public class ClusterDemoApplication {

    public static void main(String[] args) throws Exception {
        triggerSentinelInit();
        SpringApplication.run(ClusterDemoApplication.class, args);
        InitExecutor.doInit();
        DemoClusterInitFuncByZk demoClusterInitFuncByZk = new DemoClusterInitFuncByZk();
        demoClusterInitFuncByZk.init();
    }
    private static void triggerSentinelInit() {
        new Thread(() -> InitExecutor.doInit()).start();
    }
}
