package org.graalvm.tinylang;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;

// Language supporting test/program2.tiny
//@TruffleLanguage.Registration(id = "tiny")
//@ProvidedTags({ RootTag.class, RootBodyTag.class, StatementTag.class, AlwaysHalt.class })
public final class TinyLanguage2 extends TruffleLanguage<Env> {

    @Override
    protected CallTarget parse(ParsingRequest request) throws Exception {
        TinyRootNode2 parsedRoot = parse(this, request.getSource()).getNode(0);
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

    public static BytecodeRootNodes<TinyRootNode2> parse(TinyLanguage2 language, Source source) {
        return TinyRootNode2Gen.create(language, BytecodeConfig.DEFAULT, (b) -> {
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

    static RuntimeException error(String message) {
        throw new IllegalStateException(message);
    }

    @GenerateBytecode(languageClass = TinyLanguage2.class, enableBlockScoping = false, boxingEliminationTypes = {
            int.class })
    public static abstract class TinyRootNode2 extends RootNode implements BytecodeRootNode {

        protected TinyRootNode2(TinyLanguage2 language, FrameDescriptor frameDescriptor) {
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
