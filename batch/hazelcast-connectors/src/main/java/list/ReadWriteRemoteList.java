/*
 * Copyright (c) 2008-2017, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package list;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IList;
import com.hazelcast.core.ItemEvent;
import com.hazelcast.core.ItemListener;
import com.hazelcast.jet.Jet;
import com.hazelcast.jet.JetInstance;
import com.hazelcast.jet.Pipeline;
import com.hazelcast.jet.Sinks;
import com.hazelcast.jet.Sources;

/**
 * A DAG which reads from a remote Hazelcast IList,
 * converts the item to string,
 * and writes to another remote Hazelcast IList
 */
public class ReadWriteRemoteList {

    static final String SOURCE_LIST_NAME = "sourceList";
    static final String SINK_LIST_NAME = "sinkList";

    public static void main(String[] args) throws Exception {
        RemoteNode remoteNode = new RemoteNode();
        remoteNode.start();

        JetInstance instance = Jet.newJetInstance();

        try {
            ClientConfig clientConfig = new ClientConfig();
            clientConfig.getGroupConfig().setName("dev").setPassword("dev-pass");
            clientConfig.getNetworkConfig().addAddress("localhost:6701");

            Pipeline pipeline = Pipeline.create();

            pipeline.drawFrom(Sources.<Integer>readRemoteList(SOURCE_LIST_NAME, clientConfig))
                    .map(Object::toString)
                    .drainTo(Sinks.writeRemoteList(SOURCE_LIST_NAME, clientConfig));

            instance.newJob(pipeline).join();

        } finally {
            Jet.shutdownAll();
            remoteNode.stop();

        }

    }

    private static class RemoteNode {

        private static final int ITEM_COUNT = 10;

        private HazelcastInstance instance;

        void start() throws Exception {
            Config config = new Config();
            config.getNetworkConfig().setPort(6701);
            instance = Hazelcast.newHazelcastInstance(config);
            IList<Integer> sourceList = instance.getList(SOURCE_LIST_NAME);
            for (int i = 0; i < ITEM_COUNT; i++) {
                sourceList.add(i);
            }
            IList<String> sinkList = instance.getList(SINK_LIST_NAME);
            sinkList.addItemListener((ItemAddedListener<String>) event ->
                    System.out.println("Item added to sink: " + event.getItem()), true);
        }

        void stop() {
            instance.shutdown();
        }

        interface ItemAddedListener<E> extends ItemListener<E> {

            default void itemRemoved(ItemEvent<E> item) {
            }

        }
    }

}