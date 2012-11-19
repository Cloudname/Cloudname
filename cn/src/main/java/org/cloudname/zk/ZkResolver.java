package org.cloudname.zk;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooKeeper;
import org.cloudname.*;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.Matcher;


/**
 * This class is used to resolve Cloudname coordinates into endpoints.
 *
 * @author borud
 */
public final class ZkResolver implements Resolver, ZkObjectHandler.ConnectionStateChanged {

    private static final Logger log = Logger.getLogger(ZkResolver.class.getName());

    private Map<String, ResolverStrategy> strategies;

    private final ZkObjectHandler.Client zkGetter;

    private final Object dynamicAddressMonitor = new Object();

    private Map<ResolverListener, DynamicExpression> dynamicAddressesByListener = new HashMap<ResolverListener, DynamicExpression>();

    @Override
    public void connectionUp() {
        synchronized (dynamicAddressMonitor) {
            for (ResolverListener listener : dynamicAddressesByListener.keySet()) {
                listener.endpointEvent(ResolverListener.Event.CONNECTION_OK, null);
            }
        }
    }

    @Override
    public void connectionDown() {
        synchronized (dynamicAddressMonitor) {
            for (ResolverListener listener : dynamicAddressesByListener.keySet()) {
                listener.endpointEvent(ResolverListener.Event.LOST_CONNECTION, null);
            }
        }
    }

    public static class Builder {

        final private Map<String, ResolverStrategy> strategies = new HashMap<String, ResolverStrategy>();

        public Builder addStrategy(ResolverStrategy strategy) {
            strategies.put(strategy.getName(), strategy);
            return this;
        }

        public Map<String, ResolverStrategy> getStrategies() {
            return strategies;
        }

        public ZkResolver build(ZkObjectHandler.Client zkGetter) {
            return new ZkResolver(this, zkGetter);
        }

    }


    // Matches coordinate with endpoint of the form:
    // endpoint.instance.service.user.cell
    public static final Pattern endpointPattern
        = Pattern.compile( "^([a-z][a-z0-9-_]*)\\." // endpoint
                         + "(\\d+)\\." // instance
                         + "([a-z][a-z0-9-_]*)\\." // service
                         + "([a-z][a-z0-9-_]*)\\." // user
                         + "([a-z][a-z-_]*)\\z"); // cell

    // Parses abstract coordinate of the form:
    // strategy.service.user.cell.  This pattern is useful for
    // resolving hosts, but not endpoints.
    public static final Pattern strategyPattern
            = Pattern.compile( "^([a-z][a-z0-9-_]*)\\." // strategy
                        + "([a-z][a-z0-9-_]*)\\." // service
                        + "([a-z][a-z0-9-_]*)\\." // user
                        + "([a-z][a-z0-9-_]*)\\z"); // cell

    // Parses abstract coordinate of the form:
    // strategy.service.user.cell.  This pattern is useful for
    // resolving hosts, but not endpoints.
    public static final Pattern instancePattern
            = Pattern.compile( "^([a-z0-9-_]*)\\." // strategy
                        + "([a-z][a-z0-9-_]*)\\." // service
                        + "([a-z][a-z0-9-_]*)\\." // user
                        + "([a-z][a-z0-9-_]*)\\z"); // cell

    // Parses abstract coordinate of the form:
    // endpoint.strategy.service.user.cell.
    public static final Pattern endpointStrategyPattern
        = Pattern.compile( "^([a-z][a-z0-9-_]*)\\." // endpoint
                         + "([a-z][a-z0-9-_]*)\\." // strategy
                         + "([a-z][a-z0-9-_]*)\\." // service
                         + "([a-z][a-z0-9-_]*)\\." // user
                         + "([a-z][a-z0-9-_]*)\\z"); // cell


    /**
     * Inner class to keep track of parameters parsed from addressExpression.
     */
    static class Parameters {
        private String endpointName = null;
        private Integer instance = null;
        private String service = null;
        private String user = null;
        private String cell = null;
        private String strategy = null;
        private String expression = null;
        
        /**
         * Constructor that takes an addressExperssion and sets the inner variables.
         * @param addressExpression
         */
        public Parameters(String addressExpression) {
            this.expression = addressExpression;
            if (! (trySetEndPointPattern(addressExpression) ||
                   trySetStrategyPattern(addressExpression) ||
                   trySetInstancePattern(addressExpression) ||
                   trySetEndpointStrategyPattern(addressExpression))) {
                throw new IllegalStateException(
                        "Could not parse addressExpression:" + addressExpression);
            }

        }

        /**
         * Returns the original expression set in the constructor of Parameters.
         * @return expression to be resolved.
         */
        public String getExpression() {
            return expression;
        }
        
        /**
         * Returns strategy.
         * @return the string (e.g. "all" or "any", or "" if there is no strategy
         * (but instance is specified).
         */
        public String getStrategy() {
            return strategy;
        }

        /**
         * Returns endpoint name if set or "" if not set.
         * @return endpointname.
         */
        public String getEndpointName() {
            return endpointName;
        }

        /**
         * Returns instance if set or negative number if not set.
         * @return instance number.
         */
        public Integer getInstance() {
            return instance;
        }

        /**
         * Returns service
         * @return  service name.
         */
        public String getService() {
            return service;
        }

        /**
         * Returns user
         * @return user.
         */
        public String getUser() {
            return user;
        }

        /**
         * Returns cell.
         * @return cell.
         */
        public String getCell() {
            return cell;
        }

        private boolean trySetEndPointPattern(String addressExperssion) {
            Matcher m = endpointPattern.matcher(addressExperssion);
            if (! m.matches()) {
                return false;
            }
            endpointName = m.group(1);
            instance = Integer.parseInt(m.group(2));
            strategy = "";
            service = m.group(3);
            user = m.group(4);
            cell = m.group(5);
            return true;

        }

        private boolean trySetStrategyPattern(String addressExpression) {
            Matcher m = strategyPattern.matcher(addressExpression);
            if (! m.matches()) {
                return false;
            }
            endpointName = "";
            strategy = m.group(1);
            service = m.group(2);
            user = m.group(3);
            cell = m.group(4);
            instance = -1;
            return true;
        }

        private boolean trySetInstancePattern(String addressExpression) {
            Matcher m = instancePattern.matcher(addressExpression);
            if (! m.matches()) {
                return false;
            }
            endpointName = "";
            instance = Integer.parseInt(m.group(1));
            service = m.group(2);
            user = m.group(3);
            cell = m.group(4);
            strategy = "";
            return true;
        }

        private boolean trySetEndpointStrategyPattern(String addressExperssion) {
            Matcher m = endpointStrategyPattern.matcher(addressExperssion);
            if (! m.matches()) {
                return false;
            }
            endpointName = m.group(1);
            strategy = m.group(2);
            service = m.group(3);
            user = m.group(4);
            cell = m.group(5);
            instance = -1;
            return true;
        }

    }

    /**
     * Constructor, to be called from the inner Dynamic class.
     * @param builder
     */
    private ZkResolver(Builder builder, ZkObjectHandler.Client zkGetter) {
        this.strategies = builder.getStrategies();
        this.zkGetter = zkGetter;
        zkGetter.registerListener(this);
    }

    
    @Override
    public List<Endpoint> resolve(String addressExpression) throws CloudnameException {
        Parameters parameters = new Parameters(addressExpression);
            // TODO(borud): add some comments on the decision logic.  I'm
            // not sure I am too fond of the check for negative values to
            // have some particular semantics.  That smells like a problem
            // waiting to happen.

        ZooKeeper localZkPointer = zkGetter.getZookeeper();
        if (localZkPointer == null) {
            throw new CloudnameException("No connection to ZooKeeper.");
        }
        List<Integer> instances = resolveInstances(parameters, localZkPointer);

        List<Endpoint> endpoints = new ArrayList<Endpoint>();
        for (Integer instance : instances) {
            String statusPath = ZkCoordinatePath.getStatusPath(
                    parameters.getCell(), parameters.getUser(),
                    parameters.getService(), instance);

            try {
                if (! Util.exist(localZkPointer, statusPath)) {
                    continue;
                }
            } catch (InterruptedException e) {
                throw new CloudnameException(e);

            }
            final ZkCoordinateData zkCoordinateData = ZkCoordinateData.loadCoordinateData(
                    statusPath, localZkPointer, null);
            addEndpoints(zkCoordinateData.snapshot(), endpoints, parameters.getEndpointName());

        }
        if (parameters.getStrategy().equals("")) {
          return endpoints;
        }
        ResolverStrategy strategy = strategies.get(parameters.getStrategy());
        return strategy.order(strategy.filter(endpoints));
    }

    @Override
    public void removeResolverListener(final ResolverListener listener) {
        synchronized (dynamicAddressMonitor) {
            DynamicExpression expression = dynamicAddressesByListener.remove(listener);
            if (expression == null) {
                throw new IllegalArgumentException("Do not have the listener in my list.");
            }
            expression.stop();
        }
        log.fine("Removed listener.");
    }

    /**
     * The implementation does filter while listing out nodes. In this way paths that are not of
     * interest are not traversed.
     * @param filter class for filtering out endpoints
     * @return the endpoints that passes the filter
     */
    @Override
    public Set<Endpoint> getEndpoints(final Resolver.CoordinateDataFilter filter)
            throws CloudnameException, InterruptedException {

        final Set<Endpoint> endpointsIncluded = new HashSet<Endpoint>();
        final String cellPath = ZkCoordinatePath.getCloudnameRoot();
        final ZooKeeper zk =  zkGetter.getZookeeper();
        try {
            final List<String> cells = zk.getChildren(cellPath, false);
            for (final String cell : cells) {
                if (! filter.includeCell(cell)) {
                    continue;
                }
                final String userPath = cellPath + "/" + cell;
                final List<String> users = zk.getChildren(userPath, false);
                
                for (final String user : users) {
                    if (! filter.includeUser(user)) {
                        continue;
                    }
                    final String servicePath = userPath + "/" + user;
                    final List<String> services = zk.getChildren(servicePath, false);

                    for (final String service : services) {
                        if (! filter.includeService(service)) {
                            continue;
                        }
                        final String instancePath = servicePath + "/" + service;
                        final List<String> instances = zk.getChildren(instancePath, false);

                        for (final String instance : instances) {
                            final String statusPath;
                            try {
                                 statusPath = ZkCoordinatePath.getStatusPath(
                                        cell, user, service, Integer.parseInt(instance));
                            } catch (NumberFormatException e) {
                                log.log(
                                    Level.WARNING,
                                    "Got non-number as instance in cn path: " + instancePath + "/"
                                        + instance + " skipping.",
                                    e);
                                continue;
                            }

                            ZkCoordinateData zkCoordinateData = null;
                            try {
                                zkCoordinateData = ZkCoordinateData.loadCoordinateData(
                                        statusPath, zk, null);
                            } catch (CloudnameException e) {
                                // This is ok, an unclaimed node will not have status data, we
                                // ignore it even though there might also be other exception
                                // (this should be rare). The advantage is that we don't need to
                                // check if the node exists and hence reduce the load on zookeeper.
                                continue;
                            }
                            final Set<Endpoint> endpoints = zkCoordinateData.snapshot().getEndpoints();
                            for (final Endpoint endpoint : endpoints) {
                                if (filter.includeEndpointname(endpoint.getName())) {
                                    if (filter.includeServiceState(
                                            zkCoordinateData.snapshot().getServiceStatus().getState())) {
                                        endpointsIncluded.add(endpoint);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (KeeperException e) {
            throw new CloudnameException(e);
        }
        return endpointsIncluded;
    }

    @Override
    public void addResolverListener(String expression, ResolverListener listener)
            throws CloudnameException {
        final DynamicExpression dynamicExpression =
                new DynamicExpression(expression, listener, this, zkGetter);

        synchronized (dynamicAddressMonitor) {
            DynamicExpression previousExpression = dynamicAddressesByListener.put(
                    listener, dynamicExpression);
            if (previousExpression != null) {
                throw new IllegalArgumentException("It is not legal to register a listener twice.");
            }
        }
        dynamicExpression.start();
    }

    public static void addEndpoints(
            ZkCoordinateData.Snapshot statusAndEndpoints, List<Endpoint> endpoints,
            String endpointname) {
        if (statusAndEndpoints.getServiceStatus().getState() != ServiceState.RUNNING) {
            return;
        }
        if (endpointname.equals("")) {
            statusAndEndpoints.appendAllEndpoints(endpoints);
        } else {
            Endpoint e =  statusAndEndpoints.getEndpoint(endpointname);
            if (e != null) {
                endpoints.add(e);
            }
        }
    }

    private List<Integer> resolveInstances(Parameters parameters, ZooKeeper zk)
            throws CloudnameException {
        List<Integer> instances = new ArrayList<Integer>();
        if (parameters.getInstance() > -1) {
            instances.add(parameters.getInstance());
        } else {
            try {
                instances = getInstances(zk,
                        ZkCoordinatePath.coordinateWithoutInstanceAsPath(parameters.getCell(),
                                parameters.getUser(), parameters.getService()));
            } catch (InterruptedException e) {
                throw new CloudnameException(e);
            }
        }
        return instances;
    }

    private List<Integer> getInstances(ZooKeeper zk, String path)
            throws CloudnameException, InterruptedException {
        List<Integer> paths = new ArrayList<Integer>();
        try {
            List<String> children = zk.getChildren(path, false /* watcher */);
            for (String child : children) {
                paths.add(Integer.parseInt(child));
            }
        } catch (KeeperException e) {
            throw new CloudnameException(e);
        }
        return paths;
    }
}
