package org.cloudname.testtools.zookeeper;

import org.apache.zookeeper.server.ZooKeeperServer;
import org.apache.zookeeper.server.ServerConfig;
import org.apache.zookeeper.server.NIOServerCnxn;
import org.apache.zookeeper.server.persistence.FileTxnSnapLog;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.ZooDefs.Ids;

import java.util.List;
import java.io.File;
import java.io.IOException;

/**
 * Utility class to fire up an embedded ZooKeeper server in the
 * current JVM for testing purposes.
 *
 * @author borud
 */
public final class EmbeddedZooKeeper {
    private File rootDir;
    private int port;
    private NIOServerCnxn.Factory cnxnFactory;

    /**
     * Make default constructor private.
     */
    private EmbeddedZooKeeper() {}

    /**
     * @param rootDir the root directory of where the ZooKeeper
     *   instance will keep its files.  If null, a temporary directory is created
     * @param port the port where ZooKeeper will listen for client
     *   connections.
     */
    public EmbeddedZooKeeper(File rootDir, int port) {
        if (rootDir == null) {
            rootDir = createTempDir();
        }
        
        this.rootDir = rootDir;
        this.port = port;

        if (! rootDir.exists()) {
            throw new IllegalStateException("Root directory does not exist: " + rootDir);
        }
    }
    
    /**
     * Delete directory with content.
     * @param path to be deleted.
     */
    static private void deleteDirectory(File path) {
        for(File f : path.listFiles())
        {
            if(f.isDirectory()) {
                deleteDirectory(f);
                f.delete();
            } else {
                f.delete();
            }
        }
        path.delete();
    }


   /**
     * Deletes and recreates a temp dir. Sets deleteOnExit().
     * @return
     */
    private static File createTempDir() {
        File baseDir = new File(System.getProperty("java.io.tmpdir"));
        File tempDir = new File(baseDir, "EmbeddedZooKeeper");
        if (tempDir.exists()) {
            System.err.println("Deleting old instance on startup.");
            deleteDirectory(tempDir);
        }
        tempDir.mkdir();
        tempDir.deleteOnExit();
        return tempDir;
    }


    private void delDir(File path) {
        for(File f : path.listFiles())
        {
            if(f.isDirectory()) {
                delDir(f);
                f.delete();
            } else {
                f.delete();
            }
        }
        path.delete();

    }
    
    public void del() {
        File  path = new File(rootDir, "data");
        delDir(path);
    }


    /**
     * Set up the ZooKeeper instance.
     */
    public void init() throws Exception {
        // Create the data directory
        File  dataDir = new File(rootDir, "data");
        dataDir.mkdir();

        // Create server
        ServerConfig config = new ServerConfig();
        config.parse( new String[] {Integer.toString(port), dataDir.getCanonicalPath()});
        ZooKeeperServer zk = new ZooKeeperServer();
        FileTxnSnapLog ftxn = new FileTxnSnapLog(new File(config.getDataLogDir()),
                                                 new File(config.getDataDir()));
        zk.setTxnLogFactory(ftxn);
        zk.setTickTime(config.getTickTime());
        zk.setMinSessionTimeout(config.getMinSessionTimeout());
        zk.setMaxSessionTimeout(config.getMaxSessionTimeout());

        cnxnFactory = new NIOServerCnxn.Factory(config.getClientPortAddress(),
                                                config.getMaxClientCnxns());
        cnxnFactory.startup(zk);
    }

    /**
     * Shut the ZooKeeper instance down.
     */
    public void shutdown() {
        if (null != cnxnFactory) {
            cnxnFactory.shutdown();
        }
    }

    /**
     * Get the client connection string for the ZooKeeper instance.
     *
     * @return a String containing a comma-separated list of host:port
     *   entries for use as a parameter to the ZooKeeper client class.
     */
    public String getClientConnectionString() {
        return "127.0.0.1:" + port;
    }
}