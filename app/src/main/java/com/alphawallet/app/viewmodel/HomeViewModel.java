package com.alphawallet.app.viewmodel;

import static com.alphawallet.ethereum.EthereumNetworkBase.MAINNET_ID;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.alphawallet.app.BuildConfig;
import com.alphawallet.app.C;
import com.alphawallet.app.R;
import com.alphawallet.app.entity.AnalyticsProperties;
import com.alphawallet.app.entity.CryptoFunctions;
import com.alphawallet.app.entity.FragmentMessenger;
import com.alphawallet.app.entity.NetworkInfo;
import com.alphawallet.app.entity.QRResult;
import com.alphawallet.app.entity.Transaction;
import com.alphawallet.app.entity.Wallet;
import com.alphawallet.app.interact.FetchWalletsInteract;
import com.alphawallet.app.interact.GenericWalletInteract;
import com.alphawallet.app.repository.CurrencyRepositoryType;
import com.alphawallet.app.repository.EthereumNetworkBase;
import com.alphawallet.app.repository.EthereumNetworkRepository;
import com.alphawallet.app.repository.EthereumNetworkRepositoryType;
import com.alphawallet.app.repository.LocaleRepositoryType;
import com.alphawallet.app.repository.PreferenceRepositoryType;
import com.alphawallet.app.repository.TokenRepository;
import com.alphawallet.app.router.AddTokenRouter;
import com.alphawallet.app.router.ExternalBrowserRouter;
import com.alphawallet.app.router.ImportTokenRouter;
import com.alphawallet.app.router.MyAddressRouter;
import com.alphawallet.app.service.AnalyticsServiceType;
import com.alphawallet.app.service.AssetDefinitionService;
import com.alphawallet.app.service.TickerService;
import com.alphawallet.app.service.TokensService;
import com.alphawallet.app.service.TransactionsService;
import com.alphawallet.app.ui.AddTokenActivity;
import com.alphawallet.app.ui.HomeActivity;
import com.alphawallet.app.ui.ImportWalletActivity;
import com.alphawallet.app.ui.SendActivity;
import com.alphawallet.app.util.AWEnsResolver;
import com.alphawallet.app.util.QRParser;
import com.alphawallet.app.util.RateApp;
import com.alphawallet.app.util.Utils;
import com.alphawallet.app.widget.EmailPromptView;
import com.alphawallet.app.widget.QRCodeActionsView;
import com.alphawallet.token.entity.MagicLinkData;
import com.alphawallet.token.entity.MagicLinkInfo;
import com.alphawallet.token.tools.ParseMagicLink;
import com.alphawallet.token.tools.TokenDefinition;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class HomeViewModel extends BaseViewModel {
    private final String TAG = "HVM";
    public static final String ALPHAWALLET_DIR = "AlphaWallet";
    public static final String ALPHAWALLET_FILE_URL = "https://1x.alphawallet.com/dl/latest.apk";

    private final MutableLiveData<NetworkInfo> defaultNetwork = new MutableLiveData<>();
    private final MutableLiveData<Transaction[]> transactions = new MutableLiveData<>();
    private final MutableLiveData<String> backUpMessage = new MutableLiveData<>();

    private final PreferenceRepositoryType preferenceRepository;
    private final ImportTokenRouter importTokenRouter;
    private final AddTokenRouter addTokenRouter;
    private final LocaleRepositoryType localeRepository;
    private final AssetDefinitionService assetDefinitionService;
    private final GenericWalletInteract genericWalletInteract;
    private final FetchWalletsInteract fetchWalletsInteract;
    private final CurrencyRepositoryType currencyRepository;
    private final EthereumNetworkRepositoryType ethereumNetworkRepository;
    private final TransactionsService transactionsService;
    private final TickerService tickerService;
    private final MyAddressRouter myAddressRouter;
    private final AnalyticsServiceType analyticsService;
    private final ExternalBrowserRouter externalBrowserRouter;

    private CryptoFunctions cryptoFunctions;
    private ParseMagicLink parser;

    private final MutableLiveData<File> installIntent = new MutableLiveData<>();
    private final MutableLiveData<String> walletName = new MutableLiveData<>();
    private final MutableLiveData<Wallet> defaultWallet = new MutableLiveData<>();
    private final MutableLiveData<Boolean> splashActivity = new MutableLiveData<>();
    private BottomSheetDialog dialog;

    HomeViewModel(
            PreferenceRepositoryType preferenceRepository,
            LocaleRepositoryType localeRepository,
            ImportTokenRouter importTokenRouter,
            AddTokenRouter addTokenRouter,
            AssetDefinitionService assetDefinitionService,
            GenericWalletInteract genericWalletInteract,
            FetchWalletsInteract fetchWalletsInteract,
            CurrencyRepositoryType currencyRepository,
            EthereumNetworkRepositoryType ethereumNetworkRepository,
            MyAddressRouter myAddressRouter,
            TransactionsService transactionsService,
            TickerService tickerService,
            AnalyticsServiceType analyticsService,
            ExternalBrowserRouter externalBrowserRouter ) {
        this.preferenceRepository = preferenceRepository;
        this.importTokenRouter = importTokenRouter;
        this.addTokenRouter = addTokenRouter;
        this.localeRepository = localeRepository;
        this.assetDefinitionService = assetDefinitionService;
        this.genericWalletInteract = genericWalletInteract;
        this.fetchWalletsInteract = fetchWalletsInteract;
        this.currencyRepository = currencyRepository;
        this.ethereumNetworkRepository = ethereumNetworkRepository;
        this.myAddressRouter = myAddressRouter;
        this.transactionsService = transactionsService;
        this.tickerService = tickerService;
        this.analyticsService = analyticsService;
        this.externalBrowserRouter = externalBrowserRouter;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
    }

    public LiveData<Transaction[]> transactions() {
        return transactions;
    }

    public LiveData<File> installIntent() {
        return installIntent;
    }

    public LiveData<String> backUpMessage() {
        return backUpMessage;
    }

    public LiveData<Boolean> splashReset() {
        return splashActivity;
    }

    public void prepare() {
        progress.postValue(false);
        disposable = genericWalletInteract
                .find()
                .subscribe(this::onDefaultWallet, this::onError);
    }

    public void onClean()
    {

    }

    private void onDefaultWallet(final Wallet wallet)
    {
        defaultWallet.setValue(wallet);
    }

    public void showImportLink(Activity activity, String importData) {
        disposable = genericWalletInteract
                .find().toObservable()
                .filter(wallet -> checkWalletNotEqual(wallet, importData))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(wallet -> importLink(wallet, activity, importData), this::onError);
    }

    private boolean checkWalletNotEqual(Wallet wallet, String importData) {
        boolean filterPass = false;

        try {
            if (cryptoFunctions == null) {
                cryptoFunctions = new CryptoFunctions();
            }
            if (parser == null) {
                parser = new ParseMagicLink(cryptoFunctions, EthereumNetworkRepository.extraChains());
            }

            MagicLinkData data = parser.parseUniversalLink(importData);
            String linkAddress = parser.getOwnerKey(data);

            if (Utils.isAddressValid(data.contractAddress)) {
                filterPass = !wallet.address.equals(linkAddress);
            }
        } catch (Exception e) {
            if (BuildConfig.DEBUG) e.printStackTrace();
        }

        return filterPass;
    }

    private void importLink(Wallet wallet, Activity activity, String importData) {
        importTokenRouter.open(activity, importData);
    }

    public void showAddToken(Context context, String address) {
        addTokenRouter.open(context, address);
    }

    public void updateLocale(String newLocale, Context context)
    {
        localeRepository.setLocale(context, newLocale);
        //restart activity
        Intent intent = new Intent(context, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
    }

    public void downloadAndInstall(String build, Context ctx) {
        createDirectory();
        downloadAPK(build, ctx);
    }

    private void createDirectory() {
        //create XML repository directory
        File directory = new File(
                Environment.getExternalStorageDirectory()
                        + File.separator + ALPHAWALLET_DIR);

        if (!directory.exists()) {
            directory.mkdir();
        }
    }

    private void downloadAPK(String version, Context ctx) {
        String destination = Environment.getExternalStorageDirectory()
                + File.separator + ALPHAWALLET_DIR;

        File testFile = new File(destination, "AlphaWallet-" + version + ".apk");
        if (testFile.exists()) {
            testFile.delete();
        }
        final Uri uri = Uri.parse("file://" + testFile.getPath());

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(ALPHAWALLET_FILE_URL));
        request.setDescription(ctx.getString(R.string.alphawallet_update) + " " + version);
        request.setTitle(ctx.getString(R.string.app_name));
        request.setDestinationUri(uri);
        final DownloadManager manager = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
        long downloadId = manager.enqueue(request);

        //set BroadcastReceiver to install app when .apk is downloaded
        BroadcastReceiver onComplete = new BroadcastReceiver() {
            public void onReceive(Context ctxt, Intent intent) {
                installIntent.postValue(testFile);
                ctx.unregisterReceiver(this);
            }
        };

        ctx.registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    public void getWalletName(Context context) {
        disposable = fetchWalletsInteract
                .getWallet(preferenceRepository.getCurrentWalletAddress())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(wallet -> onWallet(context, wallet), this::walletError);
    }

    private void walletError(Throwable throwable)
    {
        //no wallets
        splashActivity.postValue(true);
    }

    private void onWallet(Context context, Wallet wallet)
    {
        transactionsService.changeWallet(wallet);
        if (!TextUtils.isEmpty(wallet.name))
        {
            walletName.postValue(wallet.name);
        }
        else if (!TextUtils.isEmpty(wallet.ENSname))
        {
            walletName.postValue(wallet.ENSname);
        }
        else
        {
            walletName.postValue("");
            //check for ENS name
            new AWEnsResolver(TokenRepository.getWeb3jService(MAINNET_ID), context)
                    .reverseResolveEns(wallet.address)
                    .map(ensName -> {
                        wallet.ENSname = ensName;
                        return wallet;
                    })
                    .flatMap(fetchWalletsInteract::updateENS) //store the ENS name
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeOn(Schedulers.io())
                    .subscribe(updatedWallet -> walletName.postValue(updatedWallet.ENSname), this::onENSError).isDisposed();
        }
    }

    public LiveData<String> walletName() {
        return walletName;
    }

    public void checkIsBackedUp(String walletAddress)
    {
        genericWalletInteract.getWalletNeedsBackup(walletAddress)
                .subscribe(backUpMessage::postValue).isDisposed();
    }

    public boolean isFindWalletAddressDialogShown() {
        return preferenceRepository.isFindWalletAddressDialogShown();
    }

    public void setFindWalletAddressDialogShown(boolean isShown) {
        preferenceRepository.setFindWalletAddressDialogShown(isShown);
    }

    public String getDefaultCurrency(){
        return currencyRepository.getDefaultCurrency();
    }

    public void updateTickers()
    {
        tickerService.updateTickers();
    }

    private void onENSError(Throwable throwable)
    {
        if (BuildConfig.DEBUG) throwable.printStackTrace();
    }

    public void setErrorCallback(FragmentMessenger callback)
    {
        assetDefinitionService.setErrorCallback(callback);
    }

    public void handleQRCode(Activity activity, String qrCode)
    {
        try
        {
            if (qrCode == null) return;

            QRParser parser = QRParser.getInstance(EthereumNetworkBase.extraChains());
            QRResult qrResult = parser.parse(qrCode);

            switch (qrResult.type)
            {
                case ADDRESS:
                    //showSend(activity, qrResult); //For now, direct an ETH address to send screen
                    //TODO: Issue #1504: bottom-screen popup to choose between: Add to Address book, Sent to Address, or Watch Wallet
                    showActionSheet(activity, qrResult);
                    break;
                case PAYMENT:
                    showSend(activity, qrResult);
                    break;
                case TRANSFER:
                    showSend(activity, qrResult);
                    break;
                case FUNCTION_CALL:
                    //TODO: Handle via ConfirmationActivity, need to generate function signature + data then call ConfirmationActivity
                    //TODO: Code to generate the function signature will look like the code in generateTransactionFunction
                    break;
                case URL:
                    ((HomeActivity)activity).onBrowserWithURL(qrCode);
                    break;
                case MAGIC_LINK:
                    showImportLink(activity, qrCode);
                    break;
                case OTHER:
                    qrCode = null;
                    break;
            }
        }
        catch (Exception e)
        {
            qrCode = null;
        }

        if(qrCode == null)
        {
            Toast.makeText(activity, R.string.toast_invalid_code, Toast.LENGTH_SHORT).show();
        }
    }

    private void showActionSheet(Activity activity, QRResult qrResult) {

        View.OnClickListener listener = v -> {
            if (v.getId() == R.id.send_to_this_address_action)
            {
                showSend(activity, qrResult);
            }
            else if (v.getId() == R.id.add_custom_token_action)
            {
                Intent intent = new Intent(activity, AddTokenActivity.class);
                intent.putExtra(C.EXTRA_QR_CODE, qrResult.getAddress());
                activity.startActivity(intent);
            }
            else if (v.getId() == R.id.watch_account_action)
            {
                Intent intent = new Intent(activity, ImportWalletActivity.class);
                intent.putExtra(C.EXTRA_QR_CODE, qrResult.getAddress());
                intent.putExtra(C.EXTRA_STATE, "watch");
                activity.startActivity(intent);
            }
            else if (v.getId() == R.id.open_in_etherscan_action)
            {
                NetworkInfo info = ethereumNetworkRepository.getNetworkByChain(qrResult.chainId);
                if (info == null) return;

                Uri blockChainInfoUrl = info.getEtherscanAddressUri(qrResult.getAddress());

                if (blockChainInfoUrl != Uri.EMPTY) {
                    externalBrowserRouter.open(activity, blockChainInfoUrl);
                }
            }
            else if (v.getId() == R.id.close_action)
            {
                //NOP
            }

            dialog.dismiss();
        };

        QRCodeActionsView contentView = new QRCodeActionsView(activity);

        contentView.setOnSendToAddressClickListener(listener);
        contentView.setOnAddCustonTokenClickListener(listener);


        contentView.setOnWatchWalletClickListener(listener);

        contentView.setOnOpenInEtherscanClickListener(listener);

        contentView.setOnCloseActionListener(listener);

        dialog = new BottomSheetDialog(activity);
        dialog.setContentView(contentView);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        BottomSheetBehavior behavior = BottomSheetBehavior.from((View) contentView.getParent());
        dialog.setOnShowListener(dialog -> behavior.setPeekHeight(contentView.getHeight()));
        dialog.show();
    }

    public void showSend(Activity ctx, QRResult result)
    {
        Intent intent = new Intent(ctx, SendActivity.class);
        boolean sendingTokens = (result.getFunction() != null && result.getFunction().length() > 0);
        String address = defaultWallet.getValue().address;
        int decimals = 18;

        intent.putExtra(C.EXTRA_SENDING_TOKENS, sendingTokens);
        intent.putExtra(C.EXTRA_CONTRACT_ADDRESS, address);
        intent.putExtra(C.EXTRA_NETWORKID, result.chainId);
        intent.putExtra(C.EXTRA_SYMBOL, ethereumNetworkRepository.getNetworkByChain(result.chainId).symbol);
        intent.putExtra(C.EXTRA_DECIMALS, decimals);
        intent.putExtra(C.Key.WALLET, defaultWallet.getValue());
        intent.putExtra(C.EXTRA_AMOUNT, result);
        intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        ctx.startActivity(intent);
    }

    public void showMyAddress(Activity activity)
    {
        myAddressRouter.open(activity, defaultWallet.getValue());
    }

    /**
     * This method will uniquely identify the device by creating an ID and store in preference.
     * This will be changed if user reinstall application or clear the storage explicitly.
     **/
    public void identify(Context ctx)
    {
        String uuid = preferenceRepository.getUniqueId();

        if (uuid.isEmpty())
        {
            uuid = UUID.randomUUID().toString();
        }

        analyticsService.identify(uuid);
        preferenceRepository.setUniqueId(uuid);
    }

    public void actionSheetConfirm(String mode)
    {
        AnalyticsProperties analyticsProperties = new AnalyticsProperties();
        analyticsProperties.setData(mode);

        analyticsService.track(C.AN_CALL_ACTIONSHEET, analyticsProperties);
    }

    public void stopTransactionUpdate()
    {
        transactionsService.lostFocus();
    }

    public void startTransactionUpdate()
    {
        transactionsService.startUpdateCycle();
    }

    public boolean fullScreenSelected()
    {
        return preferenceRepository.getFullScreenState();
    }

    public void tryToShowRateAppDialog(Activity context) {
        //only if installed from PlayStore
        if (Utils.verifyInstallerId(context))
        {
            RateApp.showRateTheApp(context, preferenceRepository, false);
        }
    }

    public boolean shouldShowRootWarning() {
        return preferenceRepository.showShowRootWarning();
    }

    public void setShowRootWarning(boolean shouldShow) {
        preferenceRepository.setShowRootWarning(shouldShow);
    }

    public int getUpdateWarnings() {
        return preferenceRepository.getUpdateWarningCount();
    }

    public void setUpdateWarningCount(int warns) {
        preferenceRepository.setUpdateWarningCount(warns);
    }

    public int getUpdateAsks() {
        return preferenceRepository.getUpdateAsksCount();
    }

    public void setUpdateAsksCount(int asks) {
        preferenceRepository.setUpdateAsksCount(asks);
    }

    public void setInstallTime(int time) {
        preferenceRepository.setInstallTime(time);
    }

    public void restartTokensService()
    {
        transactionsService.restartService();
    }

    public void storeCurrentFragmentId(int ordinal)
    {
        preferenceRepository.storeLastFragmentPage(ordinal);
    }

    public int getLastFragmentId()
    {
        return preferenceRepository.getLastFragmentPage();
    }

    public void tryToShowEmailPrompt(Context context, View successOverlay, Handler handler, Runnable onSuccessRunnable) {
        if (preferenceRepository.getLaunchCount() == 4) {
            EmailPromptView emailPromptView = new EmailPromptView(context, successOverlay, handler, onSuccessRunnable);
            BottomSheetDialog emailPromptDialog = new BottomSheetDialog(context, R.style.FullscreenBottomSheetDialogStyle);
            emailPromptDialog.setContentView(emailPromptView);
            emailPromptDialog.setCancelable(true);
            emailPromptDialog.setCanceledOnTouchOutside(true);
            emailPromptView.setParentDialog(emailPromptDialog);
            BottomSheetBehavior behavior = BottomSheetBehavior.from((View) emailPromptView.getParent());
            emailPromptDialog.setOnShowListener(dialog -> behavior.setPeekHeight(emailPromptView.getHeight()));
            emailPromptDialog.show();
        }
    }

    private TokenDefinition parseFile(Context ctx, InputStream xmlInputStream) throws Exception
    {
        Locale locale;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            locale = ctx.getResources().getConfiguration().getLocales().get(0);
        }
        else
        {
            locale = ctx.getResources().getConfiguration().locale;
        }

        return new TokenDefinition(
                xmlInputStream, locale, null);
    }

    public void importScriptFile(Context ctx, String importData, boolean appExternal)
    {
        try
        {
            InputStream iStream = ctx.getApplicationContext().getContentResolver().openInputStream(Uri.parse(importData));
            TokenDefinition td = parseFile(ctx, iStream);
            if (td.holdingToken == null || td.holdingToken.length() == 0) return; //tokenscript with no holding token is currently meaningless. Is this always the case?

            byte[] writeBuffer = new byte[32768];
            String newFileName = td.contracts.get(td.holdingToken).addresses.values().iterator().next().iterator().next();
            newFileName = newFileName + ".tsml";

            if (appExternal)
            {
                newFileName = ctx.getExternalFilesDir("") + File.separator + newFileName;
            }
            else
            {
                newFileName = Environment.getExternalStorageDirectory() + File.separator + ALPHAWALLET_DIR + File.separator + newFileName;
            }

            //Store
            iStream = ctx.getApplicationContext().getContentResolver().openInputStream(Uri.parse(importData));
            FileOutputStream fos = new FileOutputStream(newFileName);

            while (iStream.available() > 0)
            {
                fos.write(writeBuffer, 0, iStream.read(writeBuffer));
            }

            iStream.close();
            fos.flush();
            fos.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public boolean checkDebugDirectory()
    {
        File directory = new File(Environment.getExternalStorageDirectory()
                + File.separator + ALPHAWALLET_DIR);

        return directory.exists();
    }

    public void setWalletStartup()
    {
        TokensService.setWalletStartup();
    }

    public void setCurrencyAndLocale(Context context)
    {
        if (TextUtils.isEmpty(localeRepository.getUserPreferenceLocale()))
        {
            localeRepository.setLocale(context, localeRepository.getActiveLocale());
        }
        currencyRepository.setDefaultCurrency(preferenceRepository.getDefaultCurrency());
    }
}
