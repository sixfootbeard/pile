/**
 * Copyright 2023 John Hinchberger
 * 
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
 */
package pile.core.compiler.aot;

import pile.core.RuntimeRoot;

public class AOTMain {

    public static void main(String[] args) throws Exception {
        switch (AOTHandler.getAotType()) {
            case WRITE:
                RuntimeRoot.get("pile.core");
                AOTHandler.closeAOT();
                break;
            case READ:
                // Loads AOT Class which should load all the related classes and then archive
                // them.
                var cname = AOTHandler.CLASSNAME.replace('/', '.');
                Class.forName(cname);
                break;
            default:
                System.err.println("Unknown AOT phase");
                System.exit(-1);
        }
       
    }

}
