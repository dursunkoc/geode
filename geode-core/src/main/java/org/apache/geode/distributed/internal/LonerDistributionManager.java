/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.distributed.internal;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.*;

import org.apache.geode.CancelCriterion;
import org.apache.geode.InternalGemFireError;
import org.apache.geode.admin.GemFireHealthConfig;
import org.apache.geode.cache.CacheClosedException;
import org.apache.geode.distributed.DistributedMember;
import org.apache.geode.distributed.DurableClientAttributes;
import org.apache.geode.distributed.Role;
import org.apache.geode.distributed.internal.locks.ElderState;
import org.apache.geode.distributed.internal.membership.InternalDistributedMember;
import org.apache.geode.distributed.internal.membership.MemberAttributes;
import org.apache.geode.distributed.internal.membership.MembershipManager;
import org.apache.geode.i18n.LogWriterI18n;
import org.apache.geode.internal.Version;
import org.apache.geode.internal.cache.InternalCache;
import org.apache.geode.internal.i18n.LocalizedStrings;
import org.apache.geode.internal.logging.InternalLogWriter;
import org.apache.geode.internal.net.SocketCreator;

/**
 * A <code>LonerDistributionManager</code> is a dm that never communicates with anyone else.
 *
 *
 *
 * @since GemFire 3.5
 */
public class LonerDistributionManager implements DistributionManager {
  private final InternalDistributedSystem system;
  private final InternalLogWriter logger;
  private ElderState elderState;


  //////////////////////// Constructors ////////////////////////

  /**
   * Creates a new local distribution manager
   *
   * @param system The distributed system to which this distribution manager will send messages.
   *
   */
  public LonerDistributionManager(InternalDistributedSystem system, InternalLogWriter logger) {
    this.system = system;
    this.logger = logger;
    this.localAddress = generateMemberId();
    this.allIds = Collections.singleton(localAddress);
    this.viewMembers = new ArrayList<InternalDistributedMember>(allIds);
    DistributionStats.enableClockStats = this.system.getConfig().getEnableTimeStatistics();
  }

  ////////////////////// Instance Methods //////////////////////

  protected void startThreads() {
    // no threads needed
  }

  protected void shutdown() {
    executor.shutdown();
    try {
      executor.awaitTermination(20, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      throw new InternalGemFireError("Interrupted while waiting for DM shutdown");
    }
  }

  private final InternalDistributedMember localAddress;

  /*
   * static { // Make the id a little unique String host; try { host =
   * InetAddress.getLocalHost().getCanonicalHostName(); MemberAttributes.setDefaults(65535,
   * org.apache.geode.internal.OSProcess.getId(), DistributionManager.LONER_DM_TYPE,
   * MemberAttributes.parseRoles(system.getConfig().getRoles())); id = new
   * InternalDistributedMember(host, 65535); // noise value for port number
   *
   * } catch (UnknownHostException ex) { throw new InternalError(LocalizedStrings.
   * LonerDistributionManager_CANNOT_RESOLVE_LOCAL_HOST_NAME_TO_AN_IP_ADDRESS.toLocalizedString());
   * }
   *
   * }
   */

  private final Set<InternalDistributedMember> allIds;// = Collections.singleton(id);
  private final List<InternalDistributedMember> viewMembers;
  private ConcurrentMap<InternalDistributedMember, InternalDistributedMember> canonicalIds =
      new ConcurrentHashMap();
  private static final DummyDMStats stats = new DummyDMStats();
  private final ExecutorService executor = Executors.newCachedThreadPool();

  @Override
  public long cacheTimeMillis() {
    return this.system.getClock().cacheTimeMillis();
  }

  public InternalDistributedMember getDistributionManagerId() {
    return localAddress;
  }

  public Set getDistributionManagerIds() {
    return allIds;
  }

  public Set getDistributionManagerIdsIncludingAdmin() {
    return allIds;
  }

  public Serializable[] getDirectChannels(InternalDistributedMember[] ids) {
    return ids;
  }

  public InternalDistributedMember getCanonicalId(DistributedMember dmid) {
    InternalDistributedMember iid = (InternalDistributedMember) dmid;
    InternalDistributedMember result = this.canonicalIds.putIfAbsent(iid, iid);
    if (result != null) {
      return result;
    }
    return iid;
  }

  @Override
  public DistributedMember getMemberWithName(String name) {
    for (DistributedMember id : canonicalIds.values()) {
      if (Objects.equals(id.getName(), name)) {
        return id;
      }
    }
    if (Objects.equals(localAddress.getName(), name)) {
      return localAddress;
    }
    return null;
  }

  public Set getOtherDistributionManagerIds() {
    return Collections.EMPTY_SET;
  }

  @Override
  public Set getOtherNormalDistributionManagerIds() {
    return Collections.EMPTY_SET;
  }

  public Set getAllOtherMembers() {
    return Collections.EMPTY_SET;
  }

  @Override // DM method
  public void retainMembersWithSameOrNewerVersion(Collection<InternalDistributedMember> members,
      Version version) {
    for (Iterator<InternalDistributedMember> it = members.iterator(); it.hasNext();) {
      InternalDistributedMember id = it.next();
      if (id.getVersionObject().compareTo(version) < 0) {
        it.remove();
      }
    }
  }

  @Override // DM method
  public void removeMembersWithSameOrNewerVersion(Collection<InternalDistributedMember> members,
      Version version) {
    for (Iterator<InternalDistributedMember> it = members.iterator(); it.hasNext();) {
      InternalDistributedMember id = it.next();
      if (id.getVersionObject().compareTo(version) >= 0) {
        it.remove();
      }
    }
  }


  public Set addMembershipListenerAndGetDistributionManagerIds(MembershipListener l) {
    // return getOtherDistributionManagerIds();
    return allIds;
  }

  public Set addAllMembershipListenerAndGetAllIds(MembershipListener l) {
    return allIds;
  }

  public int getDistributionManagerCount() {
    return 0;
  }

  public InternalDistributedMember getId() {
    return getDistributionManagerId();
  }

  public boolean isAdam() {
    return true;
  }

  public InternalDistributedMember getElderId() {
    return getId();
  }

  public boolean isElder() {
    return true;
  }

  public boolean isLoner() {
    return true;
  }

  public synchronized ElderState getElderState(boolean force, boolean useTryLock) {
    // loners are always the elder
    if (this.elderState == null) {
      this.elderState = new ElderState(this);
    }
    return this.elderState;
  }

  public long getMembershipPort() {
    return 0;
  }

  public Set putOutgoingUserData(final DistributionMessage message) {
    if (message.forAll() || message.getRecipients().length == 0) {
      // do nothing
      return null;
    } else {
      throw new RuntimeException(
          LocalizedStrings.LonerDistributionManager_LONER_TRIED_TO_SEND_MESSAGE_TO_0
              .toLocalizedString(message.getRecipientsDescription()));
    }
  }

  public InternalDistributedSystem getSystem() {
    return this.system;
  }

  public void addMembershipListener(MembershipListener l) {}

  public void removeMembershipListener(MembershipListener l) {}

  public void removeAllMembershipListener(MembershipListener l) {}

  public void addAdminConsole(InternalDistributedMember p_id) {}

  public DMStats getStats() {
    return stats;
  }

  public DistributionConfig getConfig() {
    DistributionConfig result = null;
    if (getSystem() != null) {
      result = getSystem().getConfig();
    }
    return result;
  }

  public void handleManagerDeparture(InternalDistributedMember p_id, boolean crashed,
      String reason) {}

  public LogWriterI18n getLoggerI18n() {
    return this.logger;
  }

  public InternalLogWriter getInternalLogWriter() {
    return this.logger;
  }

  public ExecutorService getThreadPool() {
    return executor;
  }

  public ExecutorService getHighPriorityThreadPool() {
    return executor;
  }

  public ExecutorService getWaitingThreadPool() {
    return executor;
  }

  public ExecutorService getPrMetaDataCleanupThreadPool() {
    return executor;
  }

  @Override
  public Executor getFunctionExcecutor() {
    return executor;
  }

  public Map getChannelMap() {
    return null;
  }

  public Map getMemberMap() {
    return null;
  }

  public void close() {
    shutdown();
  }

  public void restartCommunications() {

  }

  @Override
  public List<InternalDistributedMember> getViewMembers() {
    return viewMembers;
  }

  public DistributedMember getOldestMember(Collection members) throws NoSuchElementException {
    if (members.size() == 1) {
      DistributedMember member = (DistributedMember) members.iterator().next();
      if (member.equals(viewMembers.get(0))) {
        return member;
      }
    }
    throw new NoSuchElementException(
        LocalizedStrings.LonerDistributionManager_MEMBER_NOT_FOUND_IN_MEMBERSHIP_SET
            .toLocalizedString());
  }

  public Set getAdminMemberSet() {
    return Collections.EMPTY_SET;
  }

  public static class DummyDMStats implements DMStats {
    @Override
    public long getSentMessages() {
      return 0;
    }

    @Override
    public void incSentMessages(long messages) {}

    @Override
    public void incTOSentMsg() {}

    @Override
    public long getSentCommitMessages() {
      return 0;
    }

    @Override
    public void incSentCommitMessages(long messages) {}

    @Override
    public long getCommitWaits() {
      return 0;
    }

    @Override
    public void incCommitWaits() {}

    @Override
    public long getSentMessagesTime() {
      return 0;
    }

    @Override
    public void incSentMessagesTime(long nanos) {}

    @Override
    public long getBroadcastMessages() {
      return 0;
    }

    @Override
    public void incBroadcastMessages(long messages) {}

    @Override
    public long getBroadcastMessagesTime() {
      return 0;
    }

    @Override
    public void incBroadcastMessagesTime(long nanos) {}

    @Override
    public long getReceivedMessages() {
      return 0;
    }

    @Override
    public void incReceivedMessages(long messages) {}

    @Override
    public long getReceivedBytes() {
      return 0;
    }

    @Override
    public void incReceivedBytes(long bytes) {}

    @Override
    public void incSentBytes(long bytes) {}

    @Override
    public long getProcessedMessages() {
      return 0;
    }

    @Override
    public void incProcessedMessages(long messages) {}

    @Override
    public long getProcessedMessagesTime() {
      return 0;
    }

    @Override
    public void incProcessedMessagesTime(long nanos) {}

    @Override
    public long getMessageProcessingScheduleTime() {
      return 0;
    }

    @Override
    public void incMessageProcessingScheduleTime(long nanos) {}

    @Override
    public int getOverflowQueueSize() {
      return 0;
    }

    @Override
    public void incOverflowQueueSize(int messages) {}

    @Override
    public int getNumProcessingThreads() {
      return 0;
    }

    @Override
    public void incNumProcessingThreads(int threads) {}

    @Override
    public int getNumSerialThreads() {
      return 0;
    }

    @Override
    public void incNumSerialThreads(int threads) {}

    @Override
    public void incMessageChannelTime(long val) {}

    @Override
    public void incUDPDispatchRequestTime(long val) {};

    @Override
    public long getUDPDispatchRequestTime() {
      return 0;
    };

    @Override
    public long getReplyMessageTime() {
      return 0;
    }

    @Override
    public void incReplyMessageTime(long val) {}

    @Override
    public long getDistributeMessageTime() {
      return 0;
    }

    @Override
    public void incDistributeMessageTime(long val) {}

    @Override
    public int getNodes() {
      return 0;
    }

    @Override
    public void setNodes(int val) {}

    @Override
    public void incNodes(int val) {}

    @Override
    public int getReplyWaitsInProgress() {
      return 0;
    }

    @Override
    public int getReplyWaitsCompleted() {
      return 0;
    }

    @Override
    public long getReplyWaitTime() {
      return 0;
    }

    @Override
    public long startReplyWait() {
      return 0;
    }

    @Override
    public void endReplyWait(long startNanos, long startMillis) {}

    @Override
    public void incReplyTimeouts() {}

    @Override
    public long getReplyTimeouts() {
      return 0;
    }

    @Override
    public void incReceivers() {}

    @Override
    public void decReceivers() {}

    @Override
    public void incFailedAccept() {}

    @Override
    public void incFailedConnect() {}

    @Override
    public void incReconnectAttempts() {}

    @Override
    public void incLostLease() {}

    @Override
    public void incSenders(boolean shared, boolean preserveOrder) {}

    @Override
    public void decSenders(boolean shared, boolean preserveOrder) {}

    @Override
    public int getSendersSU() {
      return 0;
    }

    @Override
    public long startSocketWrite(boolean sync) {
      return 0;
    }

    @Override
    public void endSocketWrite(boolean sync, long start, int bytesWritten, int retries) {}

    @Override
    public long startSerialization() {
      return 0;
    }

    @Override
    public void endSerialization(long start, int bytes) {}

    @Override
    public long startDeserialization() {
      return 0;
    }

    @Override
    public void endDeserialization(long start, int bytes) {}

    @Override
    public long startMsgSerialization() {
      return 0;
    }

    @Override
    public void endMsgSerialization(long start) {}

    @Override
    public long startMsgDeserialization() {
      return 0;
    }

    @Override
    public void endMsgDeserialization(long start) {}

    @Override
    public void incBatchSendTime(long start) {}

    @Override
    public void incBatchCopyTime(long start) {}

    @Override
    public void incBatchWaitTime(long start) {}

    @Override
    public void incBatchFlushTime(long start) {}

    @Override
    public void incUcastWriteBytes(int bytesWritten) {}

    @Override
    public void incMcastWriteBytes(int bytesWritten) {}

    @Override
    public void incUcastRetransmits() {}

    @Override
    public void incMcastRetransmits() {}

    @Override
    public void incMcastRetransmitRequests() {}

    @Override
    public int getMcastRetransmits() {
      return 0;
    }

    @Override
    public int getMcastWrites() {
      return 0;
    }

    @Override
    public int getMcastReads() {
      return 0;
    }

    @Override
    public void incUcastReadBytes(int amount) {}

    @Override
    public void incMcastReadBytes(int amount) {}

    @Override
    public int getAsyncSocketWritesInProgress() {
      return 0;
    }

    @Override
    public int getAsyncSocketWrites() {
      return 0;
    }

    @Override
    public int getAsyncSocketWriteRetries() {
      return 0;
    }

    @Override
    public long getAsyncSocketWriteBytes() {
      return 0;
    }

    @Override
    public long getAsyncSocketWriteTime() {
      return 0;
    }

    @Override
    public int getAsyncQueues() {
      return 0;
    }

    @Override
    public void incAsyncQueues(int inc) {}

    @Override
    public int getAsyncQueueFlushesInProgress() {
      return 0;
    }

    @Override
    public int getAsyncQueueFlushesCompleted() {
      return 0;
    }

    @Override
    public long getAsyncQueueFlushTime() {
      return 0;
    }

    @Override
    public long startAsyncQueueFlush() {
      return 0;
    }

    @Override
    public void endAsyncQueueFlush(long start) {}

    @Override
    public int getAsyncQueueTimeouts() {
      return 0;
    }

    @Override
    public void incAsyncQueueTimeouts(int inc) {}

    @Override
    public int getAsyncQueueSizeExceeded() {
      return 0;
    }

    @Override
    public void incAsyncQueueSizeExceeded(int inc) {}

    @Override
    public int getAsyncDistributionTimeoutExceeded() {
      return 0;
    }

    @Override
    public void incAsyncDistributionTimeoutExceeded() {}

    @Override
    public long getAsyncQueueSize() {
      return 0;
    }

    @Override
    public void incAsyncQueueSize(long inc) {}

    @Override
    public long getAsyncQueuedMsgs() {
      return 0;
    }

    @Override
    public void incAsyncQueuedMsgs() {}

    @Override
    public long getAsyncDequeuedMsgs() {
      return 0;
    }

    @Override
    public void incAsyncDequeuedMsgs() {}

    @Override
    public long getAsyncConflatedMsgs() {
      return 0;
    }

    @Override
    public void incAsyncConflatedMsgs() {}

    @Override
    public int getAsyncThreads() {
      return 0;
    }

    @Override
    public void incAsyncThreads(int inc) {}

    @Override
    public int getAsyncThreadInProgress() {
      return 0;
    }

    @Override
    public int getAsyncThreadCompleted() {
      return 0;
    }

    @Override
    public long getAsyncThreadTime() {
      return 0;
    }

    @Override
    public long startAsyncThread() {
      return 0;
    }

    @Override
    public void endAsyncThread(long start) {}

    @Override
    public long getAsyncQueueAddTime() {
      return 0;
    }

    @Override
    public void incAsyncQueueAddTime(long inc) {}

    @Override
    public long getAsyncQueueRemoveTime() {
      return 0;
    }

    @Override
    public void incAsyncQueueRemoveTime(long inc) {}

    @Override
    public void incReceiverBufferSize(int inc, boolean direct) {}

    @Override
    public void incSenderBufferSize(int inc, boolean direct) {}

    @Override
    public long startSocketLock() {
      return 0;
    }

    @Override
    public void endSocketLock(long start) {}

    @Override
    public long startBufferAcquire() {
      return 0;
    }

    @Override
    public void endBufferAcquire(long start) {}

    @Override
    public void incMessagesBeingReceived(boolean newMsg, int bytes) {}

    @Override
    public void decMessagesBeingReceived(int bytes) {}

    @Override
    public void incReplyHandOffTime(long start) {}

    @Override
    public int getElders() {
      return 0;
    }

    @Override
    public void incElders(int val) {}

    @Override
    public int getInitialImageMessagesInFlight() {
      return 0;
    }

    @Override
    public void incInitialImageMessagesInFlight(int val) {}

    @Override
    public int getInitialImageRequestsInProgress() {
      return 0;
    }

    @Override
    public void incInitialImageRequestsInProgress(int val) {}

    @Override
    public void incPdxSerialization(int bytesWritten) {}

    @Override
    public void incPdxDeserialization(int i) {}

    @Override
    public long startPdxInstanceDeserialization() {
      return 0;
    }

    @Override
    public void endPdxInstanceDeserialization(long start) {}

    @Override
    public void incPdxInstanceCreations() {}

    @Override
    public void incThreadOwnedReceivers(long value, int dominoCount) {}

    @Override
    public long getHeartbeatRequestsSent() {
      return 0;
    }

    @Override
    public void incHeartbeatRequestsSent() {}

    @Override
    public long getHeartbeatRequestsReceived() {
      return 0;
    }

    @Override
    public void incHeartbeatRequestsReceived() {}

    @Override
    public long getHeartbeatsSent() {
      return 0;
    }

    @Override
    public void incHeartbeatsSent() {}

    @Override
    public long getHeartbeatsReceived() {
      return 0;
    }

    @Override
    public void incHeartbeatsReceived() {}

    @Override
    public long getSuspectsSent() {
      return 0;
    }

    @Override
    public void incSuspectsSent() {}

    @Override
    public long getSuspectsReceived() {
      return 0;
    }

    @Override
    public void incSuspectsReceived() {}

    @Override
    public long getFinalCheckRequestsSent() {
      return 0;
    }

    @Override
    public void incFinalCheckRequestsSent() {}

    @Override
    public long getFinalCheckRequestsReceived() {
      return 0;
    }

    @Override
    public void incFinalCheckRequestsReceived() {}

    @Override
    public long getFinalCheckResponsesSent() {
      return 0;
    }

    @Override
    public void incFinalCheckResponsesSent() {}

    @Override
    public long getFinalCheckResponsesReceived() {
      return 0;
    }

    @Override
    public void incFinalCheckResponsesReceived() {}

    @Override
    public long getTcpFinalCheckRequestsSent() {
      return 0;
    }

    @Override
    public void incTcpFinalCheckRequestsSent() {}

    @Override
    public long getTcpFinalCheckRequestsReceived() {
      return 0;
    }

    @Override
    public void incTcpFinalCheckRequestsReceived() {}

    @Override
    public long getTcpFinalCheckResponsesSent() {
      return 0;
    }

    @Override
    public void incTcpFinalCheckResponsesSent() {}

    @Override
    public long getTcpFinalCheckResponsesReceived() {
      return 0;
    }

    @Override
    public void incTcpFinalCheckResponsesReceived() {}

    @Override
    public long getUdpFinalCheckRequestsSent() {
      return 0;
    }

    @Override
    public void incUdpFinalCheckRequestsSent() {}

    @Override
    public long getUdpFinalCheckResponsesReceived() {
      return 0;
    }

    @Override
    public void incUdpFinalCheckResponsesReceived() {}

    @Override
    public long startUDPMsgEncryption() {
      return 0;
    }

    @Override
    public void endUDPMsgEncryption(long start) {}

    @Override
    public long startUDPMsgDecryption() {
      return 0;
    }

    @Override
    public void endUDPMsgDecryption(long start) {}

    @Override
    public long getUDPMsgEncryptionTiime() {
      return 0;
    }

    @Override
    public long getUDPMsgDecryptionTime() {
      return 0;
    }
  }
  protected static class DummyExecutor implements ExecutorService {
    @Override
    public void execute(Runnable command) {
      command.run();
    }

    @Override
    public void shutdown() {}

    @Override
    public List<Runnable> shutdownNow() {
      return Collections.emptyList();
    }

    @Override
    public boolean isShutdown() {
      return false;
    }

    @Override
    public boolean isTerminated() {
      return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
      return true;
    }

    @Override
    public <T> Future<T> submit(final Callable<T> task) {
      Exception ex = null;
      T result = null;
      try {
        result = task.call();
      } catch (Exception e) {
        ex = e;
      }
      return new CompletedFuture<T>(result, ex);
    }

    @Override
    public <T> Future<T> submit(final Runnable task, final T result) {
      return submit(new Callable<T>() {
        @Override
        public T call() throws Exception {
          task.run();
          return result;
        }
      });
    }

    @Override
    public Future<?> submit(Runnable task) {
      return submit(task, null);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
        throws InterruptedException {
      List<Future<T>> results = new ArrayList<Future<T>>();
      for (Callable<T> task : tasks) {
        results.add(submit(task));
      }
      return results;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout,
        TimeUnit unit) throws InterruptedException {
      return invokeAll(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
        throws InterruptedException, ExecutionException {

      ExecutionException ex = null;
      for (Callable<T> task : tasks) {
        try {
          return submit(task).get();
        } catch (ExecutionException e) {
          ex = e;
        }
      }
      throw (ExecutionException) ex.fillInStackTrace();
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      return invokeAny(tasks);
    }
  }

  private static class CompletedFuture<T> implements Future<T> {
    private final T result;
    private final Exception ex;

    public CompletedFuture(T result, Exception ex) {
      this.result = result;
      this.ex = ex;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      return false;
    }

    @Override
    public boolean isCancelled() {
      return false;
    }

    @Override
    public boolean isDone() {
      return true;
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
      if (ex != null) {
        throw new ExecutionException(ex);
      }
      return result;
    }

    @Override
    public T get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      return get();
    }
  }

  public void throwIfDistributionStopped() {
    stopper.checkCancelInProgress(null);
  }

  /** Returns count of members filling the specified role */
  public int getRoleCount(Role role) {
    return localAddress.getRoles().contains(role) ? 1 : 0;
  }

  /** Returns true if at least one member is filling the specified role */
  public boolean isRolePresent(Role role) {
    return localAddress.getRoles().contains(role);
  }

  /** Returns a set of all roles currently in the distributed system. */
  public Set getAllRoles() {
    return localAddress.getRoles();
  }

  private int lonerPort = 0;

  // private static final int CHARS_32KB = 16384;
  private InternalDistributedMember generateMemberId() {
    InternalDistributedMember result = null;
    String host;
    try {
      // create string of the current millisecond clock time
      StringBuffer sb = new StringBuffer();
      // use low four bytes for backward compatibility
      long time = System.currentTimeMillis() & 0xffffffffL;
      for (int i = 0; i < 4; i++) {
        String hex = Integer.toHexString((int) (time & 0xff));
        if (hex.length() < 2) {
          sb.append('0');
        }
        sb.append(hex);
        time = time / 0x100;
      }
      String uniqueString = sb.toString();

      String name = this.system.getName();

      InetAddress hostAddr = SocketCreator.getLocalHost();
      host = SocketCreator.use_client_host_name ? hostAddr.getCanonicalHostName()
          : hostAddr.getHostAddress();
      DistributionConfig config = system.getConfig();
      DurableClientAttributes dac = null;
      if (config.getDurableClientId() != null) {
        dac = new DurableClientAttributes(config.getDurableClientId(),
            config.getDurableClientTimeout());
      }
      result = new InternalDistributedMember(host, lonerPort, name, uniqueString,
          ClusterDistributionManager.LONER_DM_TYPE,
          MemberAttributes.parseGroups(config.getRoles(), config.getGroups()), dac);

    } catch (UnknownHostException ex) {
      throw new InternalGemFireError(
          LocalizedStrings.LonerDistributionManager_CANNOT_RESOLVE_LOCAL_HOST_NAME_TO_AN_IP_ADDRESS
              .toLocalizedString());
    }
    return result;
  }

  /**
   * update the loner port with an integer that may be more unique than the default port (zero).
   * This updates the ID in place and establishes new default settings for the manufacture of new
   * IDs.
   *
   * @param newPort the new port to use
   */
  public void updateLonerPort(int newPort) {
    this.logger.config(LocalizedStrings.LonerDistributionmanager_CHANGING_PORT_FROM_TO,
        new Object[] {this.lonerPort, newPort, getId()});
    this.lonerPort = newPort;
    this.getId().setPort(this.lonerPort);
  }

  public boolean isCurrentMember(InternalDistributedMember p_id) {
    return getId().equals(p_id);
  }

  public Set putOutgoing(DistributionMessage msg) {
    return null;
  }

  public boolean shutdownInProgress() {
    return false;
  }

  public void removeUnfinishedStartup(InternalDistributedMember m, boolean departed) {}

  public void setUnfinishedStartups(Collection s) {}

  protected static class Stopper extends CancelCriterion {

    @Override
    public String cancelInProgress() {
      checkFailure();
      return null;
    }

    @Override
    public RuntimeException generateCancelledException(Throwable e) {
      return null;
    }
  }

  private final Stopper stopper = new Stopper();
  private volatile InternalCache cache;

  public CancelCriterion getCancelCriterion() {
    return stopper;
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.geode.distributed.internal.DM#getMembershipManager()
   */
  public MembershipManager getMembershipManager() {
    // no membership manager
    return null;
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.geode.distributed.internal.DM#getRootCause()
   */
  public Throwable getRootCause() {
    return null;
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.geode.distributed.internal.DM#setRootCause(java.lang.Throwable)
   */
  public void setRootCause(Throwable t) {}

  /*
   * (non-Javadoc)
   *
   * @see org.apache.geode.distributed.internal.DM#getMembersOnThisHost()
   *
   * @since GemFire 5.9
   */
  public Set<InternalDistributedMember> getMembersInThisZone() {
    return this.allIds;
  }

  public void acquireGIIPermitUninterruptibly() {}

  public void releaseGIIPermit() {}

  public int getDistributedSystemId() {
    return getSystem().getConfig().getDistributedSystemId();
  }

  public boolean enforceUniqueZone() {
    return system.getConfig().getEnforceUniqueHost()
        || system.getConfig().getRedundancyZone() != null;
  }

  public boolean areInSameZone(InternalDistributedMember member1,
      InternalDistributedMember member2) {
    return false;
  }

  public boolean areOnEquivalentHost(InternalDistributedMember member1,
      InternalDistributedMember member2) {
    return member1 == member2;
  }

  public Set<InternalDistributedMember> getMembersInSameZone(
      InternalDistributedMember acceptedMember) {
    return Collections.singleton(acceptedMember);
  }

  public Set<InetAddress> getEquivalents(InetAddress in) {
    Set<InetAddress> value = new HashSet<InetAddress>();
    value.add(this.getId().getInetAddress());
    return value;
  }

  public Set<DistributedMember> getGroupMembers(String group) {
    if (getDistributionManagerId().getGroups().contains(group)) {
      return Collections.singleton((DistributedMember) getDistributionManagerId());
    } else {
      return Collections.emptySet();
    }
  }

  public void addHostedLocators(InternalDistributedMember member, Collection<String> locators,
      boolean isSharedConfigurationEnabled) {
    // no-op
  }

  public Collection<String> getHostedLocators(InternalDistributedMember member) {
    return Collections.<String>emptyList();
  }

  public Map<InternalDistributedMember, Collection<String>> getAllHostedLocators() {
    return Collections.<InternalDistributedMember, Collection<String>>emptyMap();
  }

  @Override
  public Set getNormalDistributionManagerIds() {
    return getDistributionManagerIds();
  }

  @Override
  public Map<InternalDistributedMember, Collection<String>> getAllHostedLocatorsWithSharedConfiguration() {
    return Collections.<InternalDistributedMember, Collection<String>>emptyMap();
  }

  @Override
  public void forceUDPMessagingForCurrentThread() {
    // no-op for loners
  }

  @Override
  public void releaseUDPMessagingForCurrentThread() {
    // no-op for loners
  }

  @Override
  public int getDMType() {
    return 0;
  }

  @Override
  public void setCache(InternalCache instance) {
    this.cache = instance;
  }

  @Override
  public InternalCache getCache() {
    return this.cache;
  }

  @Override
  public InternalCache getExistingCache() {
    InternalCache result = this.cache;
    if (result == null) {
      throw new CacheClosedException(
          LocalizedStrings.CacheFactory_A_CACHE_HAS_NOT_YET_BEEN_CREATED.toLocalizedString());
    }
    result.getCancelCriterion().checkCancelInProgress(null);
    if (result.isClosed()) {
      throw result.getCacheClosedException(
          LocalizedStrings.CacheFactory_THE_CACHE_HAS_BEEN_CLOSED.toLocalizedString(), null);
    }
    return result;
  }

  @Override
  public HealthMonitor getHealthMonitor(InternalDistributedMember owner) {
    throw new UnsupportedOperationException(
        "getHealthMonitor is not supported by " + getClass().getSimpleName());
  }

  @Override
  public void removeHealthMonitor(InternalDistributedMember owner, int theId) {
    throw new UnsupportedOperationException(
        "removeHealthMonitor is not supported by " + getClass().getSimpleName());
  }

  @Override
  public void createHealthMonitor(InternalDistributedMember owner, GemFireHealthConfig cfg) {
    throw new UnsupportedOperationException(
        "createHealthMonitor is not supported by " + getClass().getSimpleName());
  }

  @Override
  public boolean exceptionInThreads() {
    return false;
  }

  @Override
  public void clearExceptionInThreads() {
    // no-op
  }
}
