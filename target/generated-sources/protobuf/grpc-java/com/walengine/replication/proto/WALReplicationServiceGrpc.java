package com.walengine.replication.proto;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.59.0)",
    comments = "Source: wal_replication.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class WALReplicationServiceGrpc {

  private WALReplicationServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "walengine.WALReplicationService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.walengine.replication.proto.HandshakeRequest,
      com.walengine.replication.proto.HandshakeResponse> getHandshakeMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Handshake",
      requestType = com.walengine.replication.proto.HandshakeRequest.class,
      responseType = com.walengine.replication.proto.HandshakeResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.walengine.replication.proto.HandshakeRequest,
      com.walengine.replication.proto.HandshakeResponse> getHandshakeMethod() {
    io.grpc.MethodDescriptor<com.walengine.replication.proto.HandshakeRequest, com.walengine.replication.proto.HandshakeResponse> getHandshakeMethod;
    if ((getHandshakeMethod = WALReplicationServiceGrpc.getHandshakeMethod) == null) {
      synchronized (WALReplicationServiceGrpc.class) {
        if ((getHandshakeMethod = WALReplicationServiceGrpc.getHandshakeMethod) == null) {
          WALReplicationServiceGrpc.getHandshakeMethod = getHandshakeMethod =
              io.grpc.MethodDescriptor.<com.walengine.replication.proto.HandshakeRequest, com.walengine.replication.proto.HandshakeResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Handshake"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.walengine.replication.proto.HandshakeRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.walengine.replication.proto.HandshakeResponse.getDefaultInstance()))
              .setSchemaDescriptor(new WALReplicationServiceMethodDescriptorSupplier("Handshake"))
              .build();
        }
      }
    }
    return getHandshakeMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.walengine.replication.proto.HandshakeRequest,
      com.walengine.replication.proto.ReplicationStream> getStreamEntriesMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "StreamEntries",
      requestType = com.walengine.replication.proto.HandshakeRequest.class,
      responseType = com.walengine.replication.proto.ReplicationStream.class,
      methodType = io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
  public static io.grpc.MethodDescriptor<com.walengine.replication.proto.HandshakeRequest,
      com.walengine.replication.proto.ReplicationStream> getStreamEntriesMethod() {
    io.grpc.MethodDescriptor<com.walengine.replication.proto.HandshakeRequest, com.walengine.replication.proto.ReplicationStream> getStreamEntriesMethod;
    if ((getStreamEntriesMethod = WALReplicationServiceGrpc.getStreamEntriesMethod) == null) {
      synchronized (WALReplicationServiceGrpc.class) {
        if ((getStreamEntriesMethod = WALReplicationServiceGrpc.getStreamEntriesMethod) == null) {
          WALReplicationServiceGrpc.getStreamEntriesMethod = getStreamEntriesMethod =
              io.grpc.MethodDescriptor.<com.walengine.replication.proto.HandshakeRequest, com.walengine.replication.proto.ReplicationStream>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "StreamEntries"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.walengine.replication.proto.HandshakeRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.walengine.replication.proto.ReplicationStream.getDefaultInstance()))
              .setSchemaDescriptor(new WALReplicationServiceMethodDescriptorSupplier("StreamEntries"))
              .build();
        }
      }
    }
    return getStreamEntriesMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.walengine.replication.proto.AckRequest,
      com.walengine.replication.proto.AckResponse> getAcknowledgeMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Acknowledge",
      requestType = com.walengine.replication.proto.AckRequest.class,
      responseType = com.walengine.replication.proto.AckResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.walengine.replication.proto.AckRequest,
      com.walengine.replication.proto.AckResponse> getAcknowledgeMethod() {
    io.grpc.MethodDescriptor<com.walengine.replication.proto.AckRequest, com.walengine.replication.proto.AckResponse> getAcknowledgeMethod;
    if ((getAcknowledgeMethod = WALReplicationServiceGrpc.getAcknowledgeMethod) == null) {
      synchronized (WALReplicationServiceGrpc.class) {
        if ((getAcknowledgeMethod = WALReplicationServiceGrpc.getAcknowledgeMethod) == null) {
          WALReplicationServiceGrpc.getAcknowledgeMethod = getAcknowledgeMethod =
              io.grpc.MethodDescriptor.<com.walengine.replication.proto.AckRequest, com.walengine.replication.proto.AckResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Acknowledge"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.walengine.replication.proto.AckRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.walengine.replication.proto.AckResponse.getDefaultInstance()))
              .setSchemaDescriptor(new WALReplicationServiceMethodDescriptorSupplier("Acknowledge"))
              .build();
        }
      }
    }
    return getAcknowledgeMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.walengine.replication.proto.ReplicaStatusRequest,
      com.walengine.replication.proto.ReplicaStatusResponse> getGetReplicaStatusMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetReplicaStatus",
      requestType = com.walengine.replication.proto.ReplicaStatusRequest.class,
      responseType = com.walengine.replication.proto.ReplicaStatusResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.walengine.replication.proto.ReplicaStatusRequest,
      com.walengine.replication.proto.ReplicaStatusResponse> getGetReplicaStatusMethod() {
    io.grpc.MethodDescriptor<com.walengine.replication.proto.ReplicaStatusRequest, com.walengine.replication.proto.ReplicaStatusResponse> getGetReplicaStatusMethod;
    if ((getGetReplicaStatusMethod = WALReplicationServiceGrpc.getGetReplicaStatusMethod) == null) {
      synchronized (WALReplicationServiceGrpc.class) {
        if ((getGetReplicaStatusMethod = WALReplicationServiceGrpc.getGetReplicaStatusMethod) == null) {
          WALReplicationServiceGrpc.getGetReplicaStatusMethod = getGetReplicaStatusMethod =
              io.grpc.MethodDescriptor.<com.walengine.replication.proto.ReplicaStatusRequest, com.walengine.replication.proto.ReplicaStatusResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetReplicaStatus"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.walengine.replication.proto.ReplicaStatusRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.walengine.replication.proto.ReplicaStatusResponse.getDefaultInstance()))
              .setSchemaDescriptor(new WALReplicationServiceMethodDescriptorSupplier("GetReplicaStatus"))
              .build();
        }
      }
    }
    return getGetReplicaStatusMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static WALReplicationServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<WALReplicationServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<WALReplicationServiceStub>() {
        @java.lang.Override
        public WALReplicationServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new WALReplicationServiceStub(channel, callOptions);
        }
      };
    return WALReplicationServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static WALReplicationServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<WALReplicationServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<WALReplicationServiceBlockingStub>() {
        @java.lang.Override
        public WALReplicationServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new WALReplicationServiceBlockingStub(channel, callOptions);
        }
      };
    return WALReplicationServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static WALReplicationServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<WALReplicationServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<WALReplicationServiceFutureStub>() {
        @java.lang.Override
        public WALReplicationServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new WALReplicationServiceFutureStub(channel, callOptions);
        }
      };
    return WALReplicationServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     * <pre>
     * Step 1: Replica connects and performs handshake
     * </pre>
     */
    default void handshake(com.walengine.replication.proto.HandshakeRequest request,
        io.grpc.stub.StreamObserver<com.walengine.replication.proto.HandshakeResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getHandshakeMethod(), responseObserver);
    }

    /**
     * <pre>
     * Step 2: Primary streams WAL entries to replica (server-side streaming)
     * Replica keeps this stream open indefinitely to receive real-time entries
     * </pre>
     */
    default void streamEntries(com.walengine.replication.proto.HandshakeRequest request,
        io.grpc.stub.StreamObserver<com.walengine.replication.proto.ReplicationStream> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getStreamEntriesMethod(), responseObserver);
    }

    /**
     * <pre>
     * Step 3: Replica sends periodic acknowledgements (replica → primary)
     * </pre>
     */
    default void acknowledge(com.walengine.replication.proto.AckRequest request,
        io.grpc.stub.StreamObserver<com.walengine.replication.proto.AckResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getAcknowledgeMethod(), responseObserver);
    }

    /**
     * <pre>
     * Status check — used by monitoring
     * </pre>
     */
    default void getReplicaStatus(com.walengine.replication.proto.ReplicaStatusRequest request,
        io.grpc.stub.StreamObserver<com.walengine.replication.proto.ReplicaStatusResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetReplicaStatusMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service WALReplicationService.
   */
  public static abstract class WALReplicationServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return WALReplicationServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service WALReplicationService.
   */
  public static final class WALReplicationServiceStub
      extends io.grpc.stub.AbstractAsyncStub<WALReplicationServiceStub> {
    private WALReplicationServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected WALReplicationServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new WALReplicationServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * Step 1: Replica connects and performs handshake
     * </pre>
     */
    public void handshake(com.walengine.replication.proto.HandshakeRequest request,
        io.grpc.stub.StreamObserver<com.walengine.replication.proto.HandshakeResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getHandshakeMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Step 2: Primary streams WAL entries to replica (server-side streaming)
     * Replica keeps this stream open indefinitely to receive real-time entries
     * </pre>
     */
    public void streamEntries(com.walengine.replication.proto.HandshakeRequest request,
        io.grpc.stub.StreamObserver<com.walengine.replication.proto.ReplicationStream> responseObserver) {
      io.grpc.stub.ClientCalls.asyncServerStreamingCall(
          getChannel().newCall(getStreamEntriesMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Step 3: Replica sends periodic acknowledgements (replica → primary)
     * </pre>
     */
    public void acknowledge(com.walengine.replication.proto.AckRequest request,
        io.grpc.stub.StreamObserver<com.walengine.replication.proto.AckResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getAcknowledgeMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Status check — used by monitoring
     * </pre>
     */
    public void getReplicaStatus(com.walengine.replication.proto.ReplicaStatusRequest request,
        io.grpc.stub.StreamObserver<com.walengine.replication.proto.ReplicaStatusResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetReplicaStatusMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service WALReplicationService.
   */
  public static final class WALReplicationServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<WALReplicationServiceBlockingStub> {
    private WALReplicationServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected WALReplicationServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new WALReplicationServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Step 1: Replica connects and performs handshake
     * </pre>
     */
    public com.walengine.replication.proto.HandshakeResponse handshake(com.walengine.replication.proto.HandshakeRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getHandshakeMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Step 2: Primary streams WAL entries to replica (server-side streaming)
     * Replica keeps this stream open indefinitely to receive real-time entries
     * </pre>
     */
    public java.util.Iterator<com.walengine.replication.proto.ReplicationStream> streamEntries(
        com.walengine.replication.proto.HandshakeRequest request) {
      return io.grpc.stub.ClientCalls.blockingServerStreamingCall(
          getChannel(), getStreamEntriesMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Step 3: Replica sends periodic acknowledgements (replica → primary)
     * </pre>
     */
    public com.walengine.replication.proto.AckResponse acknowledge(com.walengine.replication.proto.AckRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getAcknowledgeMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Status check — used by monitoring
     * </pre>
     */
    public com.walengine.replication.proto.ReplicaStatusResponse getReplicaStatus(com.walengine.replication.proto.ReplicaStatusRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetReplicaStatusMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service WALReplicationService.
   */
  public static final class WALReplicationServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<WALReplicationServiceFutureStub> {
    private WALReplicationServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected WALReplicationServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new WALReplicationServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Step 1: Replica connects and performs handshake
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.walengine.replication.proto.HandshakeResponse> handshake(
        com.walengine.replication.proto.HandshakeRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getHandshakeMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Step 3: Replica sends periodic acknowledgements (replica → primary)
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.walengine.replication.proto.AckResponse> acknowledge(
        com.walengine.replication.proto.AckRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getAcknowledgeMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Status check — used by monitoring
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.walengine.replication.proto.ReplicaStatusResponse> getReplicaStatus(
        com.walengine.replication.proto.ReplicaStatusRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetReplicaStatusMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_HANDSHAKE = 0;
  private static final int METHODID_STREAM_ENTRIES = 1;
  private static final int METHODID_ACKNOWLEDGE = 2;
  private static final int METHODID_GET_REPLICA_STATUS = 3;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_HANDSHAKE:
          serviceImpl.handshake((com.walengine.replication.proto.HandshakeRequest) request,
              (io.grpc.stub.StreamObserver<com.walengine.replication.proto.HandshakeResponse>) responseObserver);
          break;
        case METHODID_STREAM_ENTRIES:
          serviceImpl.streamEntries((com.walengine.replication.proto.HandshakeRequest) request,
              (io.grpc.stub.StreamObserver<com.walengine.replication.proto.ReplicationStream>) responseObserver);
          break;
        case METHODID_ACKNOWLEDGE:
          serviceImpl.acknowledge((com.walengine.replication.proto.AckRequest) request,
              (io.grpc.stub.StreamObserver<com.walengine.replication.proto.AckResponse>) responseObserver);
          break;
        case METHODID_GET_REPLICA_STATUS:
          serviceImpl.getReplicaStatus((com.walengine.replication.proto.ReplicaStatusRequest) request,
              (io.grpc.stub.StreamObserver<com.walengine.replication.proto.ReplicaStatusResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getHandshakeMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.walengine.replication.proto.HandshakeRequest,
              com.walengine.replication.proto.HandshakeResponse>(
                service, METHODID_HANDSHAKE)))
        .addMethod(
          getStreamEntriesMethod(),
          io.grpc.stub.ServerCalls.asyncServerStreamingCall(
            new MethodHandlers<
              com.walengine.replication.proto.HandshakeRequest,
              com.walengine.replication.proto.ReplicationStream>(
                service, METHODID_STREAM_ENTRIES)))
        .addMethod(
          getAcknowledgeMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.walengine.replication.proto.AckRequest,
              com.walengine.replication.proto.AckResponse>(
                service, METHODID_ACKNOWLEDGE)))
        .addMethod(
          getGetReplicaStatusMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.walengine.replication.proto.ReplicaStatusRequest,
              com.walengine.replication.proto.ReplicaStatusResponse>(
                service, METHODID_GET_REPLICA_STATUS)))
        .build();
  }

  private static abstract class WALReplicationServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    WALReplicationServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.walengine.replication.proto.WALReplicationProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("WALReplicationService");
    }
  }

  private static final class WALReplicationServiceFileDescriptorSupplier
      extends WALReplicationServiceBaseDescriptorSupplier {
    WALReplicationServiceFileDescriptorSupplier() {}
  }

  private static final class WALReplicationServiceMethodDescriptorSupplier
      extends WALReplicationServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    WALReplicationServiceMethodDescriptorSupplier(java.lang.String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (WALReplicationServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new WALReplicationServiceFileDescriptorSupplier())
              .addMethod(getHandshakeMethod())
              .addMethod(getStreamEntriesMethod())
              .addMethod(getAcknowledgeMethod())
              .addMethod(getGetReplicaStatusMethod())
              .build();
        }
      }
    }
    return result;
  }
}
