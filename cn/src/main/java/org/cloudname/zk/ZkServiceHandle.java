package org.cloudname.zk;

import org.cloudname.*;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import java.util.List;

/**
 * A service handle implementation. It does not have a lot of logic, it wraps ClaimedCoordinate, and
 * handles some config logic.
 *
 * @author borud
 */
public class ZkServiceHandle implements ServiceHandle {
    private ClaimedCoordinate claimedCoordinate;
    private static final Logger log = Logger.getLogger(ZkServiceHandle.class.getName());

    private final Coordinate coordinate;
    
    /**
     * Create a ZkServiceHandle for a given coordinate.
     *
     * @param claimedCoordinate the claimed coordinate for this service handle.
     */
    public ZkServiceHandle(ClaimedCoordinate claimedCoordinate, Coordinate coordinate) {
        this.claimedCoordinate = claimedCoordinate;
        this.coordinate = coordinate;
    }


    @Override
    public boolean waitForCoordinateOkSeconds(int seconds) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        registerCoordinateListener(new CoordinateListener() {

            @Override
            public void onCoordinateEvent(Event event, String message) {
                if (event == Event.COORDINATE_OK) {
                    latch.countDown();
                }
            }
        });
        return latch.await(seconds, TimeUnit.SECONDS);
    }


    @Override
    public void setStatus(ServiceStatus status) throws CoordinateMissingException, CloudnameException {
        claimedCoordinate.updateStatus(status);
    }

    @Override
    public void putEndpoints(List<Endpoint> endpoints) throws CoordinateMissingException, CloudnameException {
        claimedCoordinate.putEndpoints(endpoints);
    }

    @Override
    public void putEndpoint(Endpoint endpoint) throws CoordinateMissingException, CloudnameException {
        List<Endpoint> endpoints = new ArrayList<Endpoint>();
        endpoints.add(endpoint);
        putEndpoints(endpoints);
    }

    @Override
    public void removeEndpoints(List<String> names) throws CoordinateMissingException, CloudnameException {
        claimedCoordinate.removeEndpoints(names);
    }

    @Override
    public void removeEndpoint(String name) throws CoordinateMissingException, CloudnameException {
        List<String> names = new ArrayList<String>();
        names.add(name);
        removeEndpoints(names);
    }

    @Override
    public void registerConfigListener(ConfigListener listener) {
        TrackedConfig trackedConfig = new TrackedConfig(ZkCoordinatePath.getConfigPath(coordinate, null), listener);
        claimedCoordinate.registerTrackedConfig(trackedConfig);
    }

    @Override
    public void registerCoordinateListener(CoordinateListener listener)  {
        claimedCoordinate.registerCoordinateListener(listener);
    }

    @Override
    public void close() throws CloudnameException {
        claimedCoordinate.releaseClaim();
        claimedCoordinate = null;
    }

    @Override
    public CloudnameLock getCloudnameLock(CloudnameLock.Scope scope, String lockName) {
        return claimedCoordinate.getCloudnameLock(scope, lockName);
    }

    @Override
    public String toString() {
        return "Claimed coordinate instance: "+ claimedCoordinate.toString();
    }
}
