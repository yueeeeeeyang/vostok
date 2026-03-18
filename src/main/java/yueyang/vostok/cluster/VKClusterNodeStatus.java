package yueyang.vostok.cluster;

/** 集群节点状态。 */
public enum VKClusterNodeStatus {
    JOINING,
    ALIVE,
    SUSPECT,
    DEAD,
    LEFT
}
