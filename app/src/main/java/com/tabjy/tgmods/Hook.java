package com.tabjy.tgmods;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Hook implements IXposedHookLoadPackage {
    private WeakReference<Object> overridingMessageObject;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("org.telegram.messenger"))
            return;

        XposedBridge.log("package " + lpparam.packageName + " loaded!");

        Class<?> ChatMessageCell = lpparam.classLoader.loadClass("org.telegram.ui.Cells.ChatMessageCell");
        Class<?> MessageObject = lpparam.classLoader.loadClass("org.telegram.messenger.MessageObject");
        Class<?> FileLoader = lpparam.classLoader.loadClass("org.telegram.messenger.FileLoader");
        Class<?> TLRPC_Message = lpparam.classLoader.loadClass("org.telegram.tgnet.TLRPC$Message");
        Class<?> Utilities = lpparam.classLoader.loadClass("org.telegram.messenger.Utilities");

        Method getMessageObject = ChatMessageCell.getMethod("getMessageObject");
        Method isVideo = MessageObject.getMethod("isVideo");
        Method needDrawBluredPreview = MessageObject.getMethod("needDrawBluredPreview");
        Method getPathToMessage = FileLoader.getMethod("getPathToMessage", TLRPC_Message);
        Method getInternalCacheDir = FileLoader.getMethod("getInternalCacheDir");
        Method aesCtrDecryptionByteArray = Utilities.getMethod("aesCtrDecryptionByteArray", byte[].class, byte[].class, byte[].class, int.class, int.class, int.class);

        Field messageOwner = MessageObject.getField("messageOwner");
        Field ttl = TLRPC_Message.getField("ttl");

        // org.telegram.ui.ChatActivity$ChatActivityAdapter$1#didPressImage
        findAndHookMethod("org.telegram.ui.ChatActivity$ChatActivityAdapter$1", lpparam.classLoader,
                "didPressImage", "org.telegram.ui.Cells.ChatMessageCell", "float", "float",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Object message = getMessageObject.invoke(param.args[0]);

                        boolean blurred = (Boolean) needDrawBluredPreview.invoke(message);
                        if (blurred) {
                            Object owner = messageOwner.get(message);
                            ttl.set(owner, 0);

                            if ((Boolean) isVideo.invoke(message)) { // video not supported for now
                                overridingMessageObject = null;
                                return;
                            }

                            File decryptedPath = (File) getPathToMessage.invoke(null, owner);

                            if (!decryptedPath.exists()) {
                                File encryptedPath = new File(decryptedPath.getAbsolutePath() + ".enc");
                                File keyPath = new File((File) getInternalCacheDir.invoke(null), encryptedPath.getName() + ".key");

                                RandomAccessFile keyFile = new RandomAccessFile(keyPath, "r");
                                byte[] key = new byte[32];
                                byte[] iv = new byte[16];
                                keyFile.read(key);
                                keyFile.read(iv);
                                keyFile.close();

                                FileInputStream fis = new FileInputStream(encryptedPath);
                                FileOutputStream fos = new FileOutputStream(decryptedPath);

                                byte[] buffer = new byte[1024 * 4]; // 4kb buffer
                                while (true) {
                                    int n = fis.read(buffer);
                                    if (n == -1) break;

                                    aesCtrDecryptionByteArray.invoke(null, buffer, key, iv, 0, n, 0);
                                    fos.write(buffer, 0, n);
                                }
                                fis.close();
                                fos.close();
                            }
                        }

                        overridingMessageObject = new WeakReference<>(message);
                    }
                });

        // org.telegram.messenger.MessageObject#needDrawBluredPreview
        findAndHookMethod("org.telegram.messenger.MessageObject", lpparam.classLoader,
                "needDrawBluredPreview",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        boolean result = (Boolean) param.getResult();

                        if (result && overridingMessageObject != null && overridingMessageObject.get() == param.thisObject) {
                            XposedBridge.log("forcing needDrawBluredPreview() == false on " + param.thisObject.toString());
                            param.setResult(false);
                        }

                        overridingMessageObject = null;
                    }
                });
    }
}
