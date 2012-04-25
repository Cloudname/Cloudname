package org.cloudname;

/**
 * The defined states of a service.
 *
 * @author borud
 */
public enum ServiceState {
    // This means that no service has claimed the coordinate, or in
    // more practical terms: there is no ephemeral node called
    // "status" in the service root path in ZooKeeper.
    UNASSIGNED,

    // A running process has claimed the coordinate and is in the
    // process of starting up.
    STARTING,

    // A running process has claimed the coordinate and is running
    // normally.
    RUNNING,

    // A running process has claimed the coordinate and is running,
    // but it is in the process of shutting down and will not accept
    // new work.
    DRAINING,

    // An error condition has occurred.
    ERROR
}
