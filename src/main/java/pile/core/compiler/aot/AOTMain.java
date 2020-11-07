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
