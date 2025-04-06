package org.graalvm.tinylang;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.graalvm.tinylang.SExpression.Visitor;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.Variadic;
import com.oracle.truffle.api.debug.DebuggerTags.AlwaysHalt;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags.RootBodyTag;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;

@TruffleLanguage.Registration(id = "tiny")
@ProvidedTags({ RootTag.class, RootBodyTag.class, StatementTag.class, AlwaysHalt.class })
public final class TinyLanguage extends TruffleLanguage<Env> {

    @Override
    protected CallTarget parse(ParsingRequest request) throws Exception {
        TinyRootNode parsedRoot = parse4(this, request.getSource()).getNode(0);
        System.out.println(parsedRoot.dump());
        return parsedRoot.getCallTarget();
    }

    @Override
    protected Env createContext(Env env) {
        return env;
    }

    @Override
    protected boolean patchContext(Env context, Env newEnv) {
        return true;
    }

    static final class Scope {

        final Map<String, BytecodeLocal> locals = new HashMap<>();
        final Map<String, TinyRootNode> functions = new HashMap<>();

        final Scope parent;
        final String name;

        Scope(String name, Scope parent) {
            this.name = name;
            this.parent = parent;
        }

    }

    public static BytecodeRootNodes<TinyRootNode> parse4(TinyLanguage language, Source source) {
        return TinyRootNodeGen.create(language, BytecodeConfig.DEFAULT, (TinyRootNodeGen.Builder b) -> {
            b.beginSource(source);
            b.beginSourceSection(0, source.getLength());
            b.beginRoot();
            SExpression.walk(source.getCharacters().toString(), new SExpression.Visitor() {
                private Scope scope = new Scope("root", null);

                @Override
                public void onOpen(String operator, Supplier<String> identifiers) {
                    b.beginSourceSection();
                    b.beginTag(StatementTag.class);
                    switch (operator) {
                    case "add":
                        b.beginAdd();
                        break;
                    case "lt":
                        b.beginLessThan();
                        break;
                    case "while":
                        b.beginWhile();
                        break;
                    case "block":
                        b.beginBlock();
                        break;
                    case "set":
                        BytecodeLocal local = scope.locals.computeIfAbsent(identifiers.get(),
                                (localName) -> b.createLocal(localName, null));
                        b.beginStoreLocal(local);
                        break;
                    case "def":
                        String functionName = identifiers.get();
                        this.scope = new Scope(functionName, this.scope);
                        b.beginBlock();
                        b.beginRoot();
                        String argument;
                        int index = 0;
                        while ((argument = identifiers.get()) != null) {
                            BytecodeLocal argumentLocal = scope.locals.computeIfAbsent(argument,
                                    (localName) -> b.createLocal(localName, null));
                            b.beginStoreLocal(argumentLocal);
                            b.emitLoadArgument(index++);
                            b.endStoreLocal();
                        }
                        break;
                    case "call":
                        String targetName = identifiers.get();
                        TinyRootNode target = scope.functions.get(targetName);
                        if (target == null) {
                            error("Invalid function " + targetName);
                        }
                        b.beginDirectCall(target);
                        break;
                    default:
                        throw new UnsupportedOperationException();
                    }
                }

                @Override
                public void onClose(String operator, int startIndex, int length) {
                    switch (operator) {
                    case "add":
                        b.endAdd();
                        break;
                    case "lt":
                        b.endLessThan();
                        break;
                    case "while":
                        b.endWhile();
                        break;
                    case "block":
                        b.endBlock();
                        break;
                    case "set":
                        b.endStoreLocal();
                        break;
                    case "def":
                        TinyRootNode root = b.endRoot();
                        root.name = scope.name;
                        scope = scope.parent;
                        scope.functions.put(root.name, root);
                        b.endBlock();
                        break;
                    case "call":
                        b.endDirectCall();
                        break;
                    }
                    b.endTag(StatementTag.class);
                    b.endSourceSection(startIndex, length);
                }

                @Override
                public void onIdentifier(String value) {
                    BytecodeLocal local = scope.locals.get(value);
                    if (local == null) {
                        throw error("Unknown local " + value);
                    }
                    b.emitLoadLocal(local);
                }

                @Override
                public void onInteger(int value) {
                    b.emitLoadConstant(value);
                }

                @Override
                public void onDouble(double value) {
                    b.emitLoadConstant(value);
                }

                @Override
                public void onString(String value) {
                    b.emitLoadConstant(value);
                }

            });

            b.endRoot().name = "program";
            b.endSourceSection();
            b.endSource();
        });
    }

    public static BytecodeRootNodes<TinyRootNode> parse3(TinyLanguage language, Source source) {
        return TinyRootNodeGen.create(language, BytecodeConfig.DEFAULT, (b) -> {
            b.beginRoot();

            SExpression.walk(source.getCharacters().toString(), new SExpression.Visitor() {
                private Scope scope = new Scope("root", null);

                @Override
                public void onOpen(String operator, Supplier<String> identifiers) {
                    switch (operator) {
                    case "add":
                        b.beginAdd();
                        break;
                    case "lt":
                        b.beginLessThan();
                        break;
                    case "while":
                        b.beginWhile();
                        break;
                    case "block":
                        b.beginBlock();
                        break;
                    case "set":
                        BytecodeLocal local = scope.locals.computeIfAbsent(identifiers.get(),
                                (localName) -> b.createLocal(localName, null));
                        b.beginStoreLocal(local);
                        break;
                    case "def":
                        String functionName = identifiers.get();
                        this.scope = new Scope(functionName, this.scope);
                        b.beginRoot();
                        String argument;
                        int index = 0;
                        while ((argument = identifiers.get()) != null) {
                            BytecodeLocal argumentLocal = scope.locals.computeIfAbsent(argument,
                                    (localName) -> b.createLocal(localName, null));
                            b.beginStoreLocal(argumentLocal);
                            b.emitLoadArgument(index++);
                            b.endStoreLocal();
                        }
                        break;
                    case "call":
                        String targetName = identifiers.get();
                        TinyRootNode target = scope.functions.get(targetName);
                        if (target == null) {
                            error("Invalid function " + targetName);
                        }
                        b.beginDirectCall(target);
                        break;
                    }
                }

                @Override
                public void onClose(String operator, int startIndex, int length) {
                    switch (operator) {
                    case "add":
                        b.endAdd();
                        break;
                    case "lt":
                        b.endLessThan();
                        break;
                    case "while":
                        b.endWhile();
                        break;
                    case "block":
                        b.endBlock();
                        break;
                    case "set":
                        b.endStoreLocal();
                        break;
                    case "def":
                        TinyRootNode root = b.endRoot();
                        root.name = scope.name;
                        scope = scope.parent;
                        scope.functions.put(root.name, root);
                        break;
                    case "call":
                        b.endDirectCall();
                        break;
                    }
                }

                @Override
                public void onIdentifier(String value) {
                    BytecodeLocal local = scope.locals.get(value);
                    if (local == null) {
                        throw error("Unknown local " + value);
                    }
                    b.emitLoadLocal(local);
                }

                @Override
                public void onInteger(int value) {
                    b.emitLoadConstant(value);
                }

                @Override
                public void onDouble(double value) {
                    b.emitLoadConstant(value);
                }

                @Override
                public void onString(String value) {
                    b.emitLoadConstant(value);
                }

            });

            b.endRoot().name = "program";
        });
    }

    public static BytecodeRootNodes<TinyRootNode> parse2(TinyLanguage language, Source source) {
        return TinyRootNodeGen.create(language, BytecodeConfig.DEFAULT, (b) -> {
            b.beginRoot();

            SExpression.walk(source.getCharacters().toString(), new SExpression.Visitor() {
                final Map<String, BytecodeLocal> locals = new HashMap<>();

                @Override
                public void onOpen(String operator, Supplier<String> identifiers) {
                    switch (operator) {
                    case "add":
                        b.beginAdd();
                        break;
                    case "lt":
                        b.beginLessThan();
                        break;
                    case "while":
                        b.beginWhile();
                        break;
                    case "block":
                        b.beginBlock();
                        break;
                    case "set":
                        BytecodeLocal local = locals.computeIfAbsent(identifiers.get(),
                                (localName) -> b.createLocal(localName, null));
                        b.beginStoreLocal(local);
                        break;
                    }
                }

                @Override
                public void onClose(String operator, int startIndex, int length) {
                    switch (operator) {
                    case "add":
                        b.endAdd();
                        break;
                    case "lt":
                        b.endLessThan();
                        break;
                    case "while":
                        b.endWhile();
                        break;
                    case "block":
                        b.endBlock();
                        break;
                    case "set":
                        b.endStoreLocal();
                        break;
                    }
                }

                @Override
                public void onIdentifier(String value) {
                    BytecodeLocal local = locals.get(value);
                    if (local == null) {
                        throw error("Unknown local " + value);
                    }
                    b.emitLoadLocal(local);
                }

                @Override
                public void onInteger(int value) {
                    b.emitLoadConstant(value);
                }

                @Override
                public void onDouble(double value) {
                    b.emitLoadConstant(value);
                }

                @Override
                public void onString(String value) {
                    b.emitLoadConstant(value);
                }

            });

            b.endRoot();
        });
    }

    public static BytecodeRootNodes<TinyRootNode> parse1(TinyLanguage language, Source source) {
        return TinyRootNodeGen.create(language, BytecodeConfig.DEFAULT, (b) -> {
            b.beginRoot();

            SExpression.walk(source.getCharacters().toString(), new Visitor() {

                @Override
                public void onOpen(String operator, Supplier<String> arguments) {
                    switch (operator) {
                    case "add":
                        b.beginAdd();
                        break;
                    }
                }

                @Override
                public void onIdentifier(String value) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void onInteger(int value) {
                    b.emitLoadConstant(value);
                }

                @Override
                public void onDouble(double value) {
                    b.emitLoadConstant(value);
                }

                @Override
                public void onString(String value) {
                    b.emitLoadConstant(value);
                }

                @Override
                public void onClose(String operator, int startIndex, int length) {
                    switch (operator) {
                    case "add":
                        b.endAdd();
                        break;
                    }
                }
            });

            b.endRoot();
        });
    }

    static RuntimeException error(String message) {
        throw new IllegalStateException(message);
    }

    @GenerateBytecode(languageClass = TinyLanguage.class, enableBlockScoping = false, boxingEliminationTypes = {
            int.class }, enableTagInstrumentation = true)
    public static abstract class TinyRootNode extends RootNode implements BytecodeRootNode {

        protected TinyRootNode(TinyLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        String name;

        @Operation
        static final class Add {
            @Specialization
            static int doInt(int a, int b) {
                return a + b;
            }
        }

        @Operation
        static final class LessThan {
            @Specialization
            static boolean doInt(int a, int b) {
                return a < b;
            }
        }

        @Operation
        @ConstantOperand(name = "target", type = TinyRootNode.class)
        static final class DirectCall {

            @Specialization
            static Object doDirect(TinyRootNode target, @Variadic Object[] args, @Bind Node location) {
                return target.getCallTarget().call(location, args);
            }

        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }

        // TODO implement language here

    }
}
