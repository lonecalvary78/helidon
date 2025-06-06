/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.grpc;

import io.helidon.grpc.core.WeightedBag;
import io.helidon.http.HttpPrologue;
import io.helidon.http.PathMatcher;
import io.helidon.http.PathMatchers;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import io.grpc.BindableService;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.ServerCalls;

class GrpcRouteHandler<ReqT, ResT> extends GrpcRoute {

    private final MethodDescriptor<ReqT, ResT> method;
    private final PathMatcher pathMatcher;
    private final ServerCallHandler<ReqT, ResT> callHandler;
    private final Descriptors.FileDescriptor proto;

    private GrpcRouteHandler(MethodDescriptor<ReqT, ResT> method,
                             PathMatcher pathMatcher,
                             ServerCallHandler<ReqT, ResT> callHandler,
                             Descriptors.FileDescriptor proto) {
        this.method = method;
        this.pathMatcher = pathMatcher;
        this.callHandler = callHandler;
        this.proto = proto;
    }

    @Override
    String serviceName() {
        return method.getServiceName();
    }

    /**
     * Provides access to proto file descriptor.
     *
     * @return descriptor or {@code null} if not defined.
     */
    @Override
    public Descriptors.FileDescriptor proto() {
        return proto;
    }

    static <ReqT, ResT> GrpcRouteHandler<ReqT, ResT> unary(Descriptors.FileDescriptor proto,
                                                           String serviceName,
                                                           String methodName,
                                                           ServerCalls.UnaryMethod<ReqT, ResT> method,
                                                           WeightedBag<ServerInterceptor> interceptors) {
        ServerCallHandler<ReqT, ResT> callHandler = GrpcInterceptorUtil.interceptHandler(
                ServerCalls.asyncUnaryCall(method), interceptors);
        return grpc(proto, serviceName, methodName, callHandler);
    }

    static <ReqT, ResT> GrpcRouteHandler<ReqT, ResT> bidi(Descriptors.FileDescriptor proto,
                                                          String serviceName,
                                                          String methodName,
                                                          ServerCalls.BidiStreamingMethod<ReqT, ResT> method,
                                                          WeightedBag<ServerInterceptor> interceptors) {
        ServerCallHandler<ReqT, ResT> callHandler = GrpcInterceptorUtil.interceptHandler(
                ServerCalls.asyncBidiStreamingCall(method), interceptors);
        return grpc(proto, serviceName, methodName, callHandler);
    }

    static <ReqT, ResT> GrpcRouteHandler<ReqT, ResT> serverStream(Descriptors.FileDescriptor proto,
                                                                  String serviceName,
                                                                  String methodName,
                                                                  ServerCalls.ServerStreamingMethod<ReqT, ResT> method,
                                                                  WeightedBag<ServerInterceptor> interceptors) {
        ServerCallHandler<ReqT, ResT> callHandler = GrpcInterceptorUtil.interceptHandler(
                ServerCalls.asyncServerStreamingCall(method), interceptors);
        return grpc(proto, serviceName, methodName, callHandler);
    }

    static <ReqT, ResT> GrpcRouteHandler<ReqT, ResT> clientStream(Descriptors.FileDescriptor proto,
                                                                  String serviceName,
                                                                  String methodName,
                                                                  ServerCalls.ClientStreamingMethod<ReqT, ResT> method,
                                                                  WeightedBag<ServerInterceptor> interceptors) {
        ServerCallHandler<ReqT, ResT> callHandler = GrpcInterceptorUtil.interceptHandler(
                ServerCalls.asyncClientStreamingCall(method), interceptors);
        return grpc(proto, serviceName, methodName, callHandler);
    }

    /**
     * Create a {@link GrpcRouteHandler gRPC route} from a {@link ServerMethodDefinition}.
     *
     * @param definition the {@link ServerMethodDefinition} representing the method to execute
     * @param proto an optional protocol buffer {@link com.google.protobuf.Descriptors.FileDescriptor}
     * containing the service definition
     * @param interceptors the interceptors
     * @param <ReqT> the request type
     * @param <ResT> the response type
     * @return a {@link GrpcRouteHandler gRPC route} created
     * from the {@link ServerMethodDefinition}
     */
    static <ReqT, ResT> GrpcRouteHandler<ReqT, ResT> methodDefinition(ServerMethodDefinition<ReqT, ResT> definition,
                                                                      Descriptors.FileDescriptor proto,
                                                                      WeightedBag<ServerInterceptor> interceptors) {
        ServerCallHandler<ReqT, ResT> callHandler = GrpcInterceptorUtil.interceptHandler(
                definition.getServerCallHandler(), interceptors);
        return grpc(definition.getMethodDescriptor(), callHandler, proto);
    }

    @SuppressWarnings("unchecked")
    public static <ReqT, ResT> GrpcRouteHandler<ReqT, ResT> bindableMethod(BindableService service,
                                                                           ServerMethodDefinition<?, ?> method,
                                                                           WeightedBag<ServerInterceptor> interceptors) {
        ServerServiceDefinition definition = service.bindService();
        String path = definition.getServiceDescriptor().getName() + "/"
                + method.getMethodDescriptor().getBareMethodName();
        ServerCallHandler<ReqT, ResT> callHandler = GrpcInterceptorUtil.interceptHandler(
                (ServerCallHandler<ReqT, ResT>) method.getServerCallHandler(),
                interceptors,
                service instanceof BindableServiceImpl svc ? svc.serviceDescriptor() : null);
        return new GrpcRouteHandler<>((MethodDescriptor<ReqT, ResT>) method.getMethodDescriptor(),
                                      PathMatchers.exact(path),
                                      callHandler,
                                      null);
    }

    @Override
    GrpcRouteHandler<?, ?> handler(HttpPrologue grpcPrologue) {
        return this;
    }

    PathMatchers.MatchResult accepts(HttpPrologue prologue) {
        return pathMatcher.match(prologue.uriPath());
    }

    MethodDescriptor<ReqT, ResT> method() {
        return method;
    }

    ServerCallHandler<ReqT, ResT> callHandler() {
        return callHandler;
    }

    private static <ResT, ReqT> GrpcRouteHandler<ReqT, ResT> grpc(Descriptors.FileDescriptor proto,
                                                                  String serviceName,
                                                                  String methodName,
                                                                  ServerCallHandler<ReqT, ResT> callHandler) {
        String simpleName = getSimpleName(serviceName);
        Descriptors.ServiceDescriptor svc = proto.findServiceByName(simpleName);
        if (svc == null) {
            throw new IllegalArgumentException("Unable to find gRPC service '" + simpleName + "'");
        }
        Descriptors.MethodDescriptor mtd = svc.findMethodByName(methodName);
        if (mtd == null) {
            throw new IllegalArgumentException("Unable to find gRPC method '" + methodName
                    + "' in service '" + simpleName + "'");
        }
        String path = svc.getFullName() + "/" + methodName;

        /*
        We have to use reflection here
         - to load the class
         - to invoke a static method on it
         */
        Class<ReqT> requestType = load(getClassName(mtd.getInputType()));
        Class<ResT> responseType = load(getClassName(mtd.getOutputType()));

        MethodDescriptor.Marshaller<ReqT> reqMarshaller = ProtoMarshaller.get(requestType);
        MethodDescriptor.Marshaller<ResT> resMarshaller = ProtoMarshaller.get(responseType);

        MethodDescriptor.Builder<ReqT, ResT> grpcDesc = MethodDescriptor.<ReqT, ResT>newBuilder()
                .setFullMethodName(MethodDescriptor.generateFullMethodName(simpleName, methodName))
                .setType(getMethodType(mtd)).setFullMethodName(path).setRequestMarshaller(reqMarshaller)
                .setResponseMarshaller(resMarshaller).setSampledToLocalTracing(true);

        return new GrpcRouteHandler<>(grpcDesc.build(), PathMatchers.exact(path), callHandler, proto);
    }


    /**
     * Create a {@link GrpcRouteHandler gRPC route} from a {@link MethodDescriptor}.
     *
     * @param grpcDesc the {@link MethodDescriptor} describing the method to execute
     * @param callHandler the {@link io.grpc.ServerCallHandler} that will execute the method
     * @param proto an optional protocol buffer {@link com.google.protobuf.Descriptors.FileDescriptor} containing
     * the service definition
     * @param <ReqT> the request type
     * @param <ResT> the response type
     * @return a {@link GrpcRouteHandler gRPC route} created
     * from the {@link io.grpc.ServerMethodDefinition}
     */
    private static <ResT, ReqT> GrpcRouteHandler<ReqT, ResT> grpc(MethodDescriptor<ReqT, ResT> grpcDesc,
                                                                  ServerCallHandler<ReqT, ResT> callHandler,
                                                                  Descriptors.FileDescriptor proto) {
        return new GrpcRouteHandler<>(grpcDesc, PathMatchers.exact(grpcDesc.getFullMethodName()), callHandler, proto);
    }

    private static String getClassName(Descriptors.Descriptor descriptor) {
        Descriptors.FileDescriptor fd = descriptor.getFile();
        String outerClass = getOuterClass(fd);
        String pkg = fd.getOptions().getJavaPackage();
        pkg = "".equals(pkg) ? fd.getPackage() : pkg;
        return pkg + "." + outerClass + descriptor.getName().replace('.', '$');
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<T> load(String className) {
        try {
            return (Class<T>) GrpcRouteHandler.class.getClassLoader().loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Failed to load class \"" + className + "\" for grpc", e);
        }
    }

    private static String getOuterClass(Descriptors.FileDescriptor proto) {
        DescriptorProtos.FileOptions options = proto.getOptions();
        if (options.getJavaMultipleFiles()) {
            // there is no outer class -- each message will have its own top-level class
            return "";
        }

        String outerClass = options.getJavaOuterClassname();
        if ("".equals(outerClass)) {
            outerClass = getOuterClassFromFileName(proto.getName());
        }

        // append $ in order to timed a proper binary name for the nested message class
        return outerClass + "$";
    }

    private static String getOuterClassFromFileName(String name) {
        // strip .proto extension
        name = name.substring(0, name.lastIndexOf(".proto"));

        String[] words = name.split("_");
        StringBuilder sb = new StringBuilder(name.length());

        for (String word : words) {
            sb.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1));
        }

        return sb.toString();
    }

    private static MethodDescriptor.MethodType getMethodType(Descriptors.MethodDescriptor mtd) {
        if (mtd.isClientStreaming()) {
            if (mtd.isServerStreaming()) {
                return MethodDescriptor.MethodType.BIDI_STREAMING;
            } else {
                return MethodDescriptor.MethodType.CLIENT_STREAMING;
            }
        } else if (mtd.isServerStreaming()) {
            return MethodDescriptor.MethodType.SERVER_STREAMING;
        }
        return MethodDescriptor.MethodType.UNARY;
    }

    private static String getSimpleName(String fullName) {
        int k = fullName.lastIndexOf('.');
        return k < 0 ? fullName : fullName.substring(k + 1);
    }
}
