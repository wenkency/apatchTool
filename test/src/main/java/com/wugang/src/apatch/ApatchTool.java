package com.wugang.src.apatch;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import android.webkit.URLUtil;

import com.alipay.euler.andfix.patch.PatchManager;

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import cn.aigestudio.downloader.bizs.DLError;
import cn.aigestudio.downloader.bizs.DLManager;
import cn.aigestudio.downloader.interfaces.IDListener;
import cn.aigestudio.downloader.interfaces.SimpleDListener;

/**
 * 深圳州富科技有限公司
 * Created by lwg on 2016/9/20.
 * 阿里巴巴的patch管理，工具类
 */
public class ApatchTool {
    private static ApatchTool ourInstance = new ApatchTool();
    /**
     * Patch管理
     */
    private PatchManager patchManager;
    private Context context;
    private int reRequestCount = 1;//当前重试次数
    private final int reRequestMaxCount = 3;//最大重试次数
    public static final String TAG = "ApatchTool";
    private OnApatchToolListener onApatchToolListener;

    public static ApatchTool getInstance() {
        return ourInstance;
    }

    private ApatchTool() {

    }

    /**
     * 初始化
     *
     * @param context
     */
    public ApatchTool init(Context context) {
        if (patchManager != null) {
            return ourInstance;
        }
        patchManager = new PatchManager(context);
        this.context = context;
        try {
            String appversion = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
            patchManager.init(appversion);
            patchManager.loadPatch();
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return ourInstance;
    }

    /**
     * 添加 path到PatchManager，
     *
     * @param path 本地路径或者服务器路径
     * @return
     */
    public ApatchTool addPatch(String path) {
        try {
            if (URLUtil.isHttpUrl(path) || URLUtil.isHttpsUrl(path)) {
                downloadPath(path);
            } else {
                patchManager.addPatch(path);
            }
            if(onApatchToolListener!=null)
                onApatchToolListener.onCompleted();
        } catch (IOException e) {
            e.printStackTrace();
            if(onApatchToolListener!=null)
                onApatchToolListener.onError(e.getLocalizedMessage());
        }
        return ourInstance;
    }

    /**
     * 下载服务器的path
     *
     * @param path
     */
    private void downloadPath(final String path) {
        DLManager.getInstance(context)
                .dlStart(path, new SimpleDListener() {
                    @Override
                    public void onFinish(File file) {
                        Log.e(TAG, "onFinish: " + file);
                        if(onApatchToolListener!=null)
                            onApatchToolListener.onCompleted();
                        //下载完成之后添加patch
                        try {
                            File renameFile = new File(file.getParent(), System.currentTimeMillis() + "_" + file.getName());
                            file.renameTo(renameFile);
                            patchManager.addPatch(renameFile.getAbsolutePath());
                            //patch添加之后删除源文件
                            //file.delete();
                            //renameFile.delete();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onProgress(int progress) {
                        super.onProgress(progress);
                        Log.e(TAG, "onProgress: " + progress);
                    }

                    @Override
                    public void onError(int status, String error) {
                        super.onError(status, error);
                        String msg = error;
                        if (status == DLError.ERROR_INVALID_URL) {
                            msg = "onError: 无效的patch路径";
                            Log.e(TAG, msg);
                            return;
                        } else if (status == DLError.ERROR_NOT_NETWORK) {
                            msg = "onError: 无网络";
                            Log.e(TAG,msg);
                            return;
                        }
                        Log.e(TAG, "onError: " + error);
                        if (reRequestCount <= reRequestMaxCount) {
                            reRequestCount++;
                            //下载失败，重试
                            new Timer().schedule(new TimerTask() {
                                @Override
                                public void run() {
                                    downloadPath(path);
                                }
                            }, 2000);
                        }else{
                            if(onApatchToolListener!=null)
                                onApatchToolListener.onError(msg);
                        }
                    }
                });
    }

    public ApatchTool setOnApatchToolListener(OnApatchToolListener onApatchToolListener) {
        this.onApatchToolListener = onApatchToolListener;
        return this;
    }

    /**
     * 获取patch管理
     *
     * @return
     */
    public PatchManager getPatchManager() {
        return patchManager;
    }

    public interface OnApatchToolListener{
        void onCompleted();
        void onError(String errorMsg);
    }
}
