package org.cloudname.zk;

import org.cloudname.*;

import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.KeeperException;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import java.util.concurrent.CountDownLatch;

import java.io.IOException;


/**
 * An implementation of Cloudname using ZooKeeper.
 *
 * This implementation assumes that the path prefix defined by
 * CN_PATH_PREFIX is only used by Cloudname.  The structure and
 * semantics of things under this prefix are defined by this library
 * and will be subject to change.
 *
 * TODO(borud):
 *
 *  - We need a recovery mechanism for when the ZK server we are
 *    connected to goes down.
 *
 *  - when the ZkCloudname instance is releaseClaim()d the handles should
 *    perhaps be invalidated.
 *
 *  - The exception handling in this class is just atrocious.
 *
 * @author borud
 */
public class ZkCloudname implements Cloudname, Watcher {

    private static final int SESSION_TIMEOUT = 5000;

    private static final Logger log = Logger.getLogger(ZkCloudname.class.getName());

    // Instance variables
    private ZooKeeper zk;
    private final String connectString;

    // Latches that count down when ZooKeeper is connected
    private final CountDownLatch connectedSignal = new CountDownLatch(1);


    private ZkCloudname(Builder builder) {
        connectString = builder.getConnectString();
    }

    /**
     * Connect to ZooKeeper instance with time-out value.
     * @param waitTime time-out value for establishing connection.
     * @param waitUnit time unit for time-out when establishing connection.
     * @throws org.cloudname.CloudnameException.CouldNotConnectToStorage if connection can not be established
     * @return
     */
    public ZkCloudname connectWithTimeout(long waitTime, TimeUnit waitUnit) {

        try {
            zk = new ZooKeeper(connectString, SESSION_TIMEOUT, this);
            if (! connectedSignal.await(waitTime, waitUnit)) {
                throw new CloudnameException.CouldNotConnectToStorage(
                        "Connecting to ZooKeeper timed out.");
            }
            log.info("Connected to ZooKeeper " + connectString);
        } catch (IOException e) {
            throw new CloudnameException.CouldNotConnectToStorage(e);
        } catch (InterruptedException e) {
            throw new CloudnameException.CouldNotConnectToStorage(e);
        }

        return this;
    }

    /**
     * Connect to ZooKeeper instance with long time-out, however, it might fail fast.
     * @return
     */
    public ZkCloudname connect() {
        // We wait up to 100 years.
        return connectWithTimeout(365 * 100, TimeUnit.DAYS);
    }
    
    @Override
    public void process(WatchedEvent event) {
        log.fine("Got event " + event.toString());

        // Initial connection to ZooKeeper is completed.
        if (event.getState() == Event.KeeperState.SyncConnected) {
            if (connectedSignal.getCount() == 0) {
                // I am not sure if this can ever occur, but until I
                // know I'll just leave this log message in here.
                log.info("The connectedSignal count was already zero.  Duplicate Event.KeeperState.SyncConnected");
            }
            connectedSignal.countDown();
        }
    }

    /**
     * Create a given coordinate in the ZooKeeper node tree.
     *
     * Just blindly creates the entire path.  Elements of the path may
     * exist already, but it seems wasteful to
     */
    @Override
    public void createCoordinate(Coordinate coordinate) {
        // Create the root path for the coordinate.  We do this
        // blindly, meaning that if the path already exists, then
        // that's ok -- so a more correct name for this method would
        // be ensureCoordinate(), but that might confuse developers.
        String root = ZkCoordinatePath.getRoot(coordinate);
        try {
            Util.mkdir(zk, root, Ids.OPEN_ACL_UNSAFE);
        } catch (KeeperException e) {
            throw new RuntimeException(e);
        }

        // Create the nodes that represent subdirectories.
        String configPath = ZkCoordinatePath.getConfigPath(coordinate, null);
        try {
            log.info("Creating config node " + configPath);
            zk.create(configPath, null, Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        } catch (KeeperException e) {
            throw new CloudnameException(e);
        } catch (InterruptedException e) {
            throw new CloudnameException(e);
        }
    }

    /**
     * Claim a coordinate.
     *
     * In this implementation a coordinate is claimed by creating an
     * ephemeral with the name defined in CN_STATUS_NAME.  If the node
     * already exists the coordinate has already been claimed.
     */
    @Override
    public ServiceHandle claim(Coordinate coordinate) {
        String statusPath = ZkCoordinatePath.getStatusPath(coordinate);
        log.info("Claiming " + coordinate.asString() + " (" + statusPath + ")");

        ZkStatusAndEndpoints statusAndEndpoints = new ZkStatusAndEndpoints.Builder(zk, statusPath).claim().build();
        // If we have come thus far we have succeeded in creating the
        // CN_STATUS_NAME node within the service coordinate directory
        // in ZooKeeper and we can give the client a ServiceHandle.

        return new ZkServiceHandle(coordinate, statusAndEndpoints);
    }

    @Override
    public Resolver getResolver() {
        return new ZkResolver.Builder(zk).addStrategy(new StrategyAll()).addStrategy(new StrategyAny()).build();
    }

    @Override
    public ServiceStatus getStatus(Coordinate coordinate) {
        String statusPath = ZkCoordinatePath.getStatusPath(coordinate);
        ZkStatusAndEndpoints statusAndEndpoints = new ZkStatusAndEndpoints.Builder(zk, statusPath).load().build();
        return statusAndEndpoints.getServiceStatus();
    }

    /**
     * Close the connection to ZooKeeper.
     */
    public void close() {
        if (null == zk) {
            throw new IllegalStateException("Cannot releaseClaim(): Not connected to ZooKeeper");
        }

        try {
            zk.close();
            log.info("ZooKeeper session closed for " + connectString);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }


    /**
     *  This class builds parameters for ZkCloudname.
     */
    static class Builder {
        private String connectString;

        public Builder setConnectString(String connectString) {
            this.connectString = connectString;
            return this;
        }

        // TODO(borud, dybdahl): Make this smarter, some ideas:
        //                       Connect to one node and read from a magic path
        //                       how many zookeepers that are running and build
        //                       the path based on this information.
        public Builder autoConnect() {
            this.connectString = "z1:2181,z2:2181,z3:2181";
            return this;
        }

        public String getConnectString() {
            return connectString;
        }

        public ZkCloudname build() {
            return new ZkCloudname(this);
        }
    }
}