package io.awallet.crypto.alphawallet.ui;

import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import io.awallet.crypto.alphawallet.R;
import io.awallet.crypto.alphawallet.entity.FinishReceiver;
import io.awallet.crypto.alphawallet.entity.Ticket;
import io.awallet.crypto.alphawallet.entity.Token;
import io.awallet.crypto.alphawallet.entity.TokenInfo;
import io.awallet.crypto.alphawallet.ui.widget.adapter.TicketAdapter;
import io.awallet.crypto.alphawallet.viewmodel.AssetDisplayViewModel;
import io.awallet.crypto.alphawallet.viewmodel.AssetDisplayViewModelFactory;
import io.awallet.crypto.alphawallet.widget.ProgressView;
import io.awallet.crypto.alphawallet.widget.SystemView;

import javax.inject.Inject;

import dagger.android.AndroidInjection;
import io.stormbird.token.entity.TicketRange;

import static io.awallet.crypto.alphawallet.C.Key.TICKET;

/**
 * Created by James on 22/01/2018.
 */

/**
 *
 */
public class AssetDisplayActivity extends BaseActivity implements View.OnClickListener
{
    @Inject
    protected AssetDisplayViewModelFactory assetDisplayViewModelFactory;
    private AssetDisplayViewModel viewModel;
    private SystemView systemView;
    private ProgressView progressView;
    private RecyclerView list;

    private FinishReceiver finishReceiver;

    private Ticket ticket;
    private TicketAdapter adapter;
    private String balance = null;
    private String burnList = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState)
    {
        AndroidInjection.inject(this);

        ticket = getIntent().getParcelableExtra(TICKET);

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_asset_display);
        toolbar();

        setTitle(getString(R.string.title_show_tickets));
        TokenInfo info = ticket.tokenInfo;

        systemView = findViewById(R.id.system_view);
        systemView.hide();
        progressView = findViewById(R.id.progress_view);
        progressView.hide();

        list = findViewById(R.id.listTickets);

        adapter = new TicketAdapter(this, this::onTicketIdClick, ticket);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        list.setHapticFeedbackEnabled(true);

        viewModel = ViewModelProviders.of(this, assetDisplayViewModelFactory)
                .get(AssetDisplayViewModel.class);

        viewModel.queueProgress().observe(this, progressView::updateProgress);
        viewModel.pushToast().observe(this, this::displayToast);
        viewModel.ticket().observe(this, this::onTokenUpdate);

        findViewById(R.id.button_use).setOnClickListener(this);
        findViewById(R.id.button_sell).setOnClickListener(this);
        findViewById(R.id.button_transfer).setOnClickListener(this);

        finishReceiver = new FinishReceiver(this);
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        viewModel.prepare(ticket);
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        unregisterReceiver(finishReceiver);
    }

    private void onTokenUpdate(Token t)
    {
        ticket = (Ticket)t;
        if (!ticket.getBurnListStr().equals(burnList) || !ticket.getFullBalance().equals(balance))
        {
            adapter.setTicket(ticket);
            RecyclerView list = findViewById(R.id.listTickets);
            list.setAdapter(null);
            list.setAdapter(adapter);
            balance = ticket.getFullBalance();
            burnList = ticket.getBurnListStr();
        }
    }

    @Override
    public void onClick(View v)
    {
        switch (v.getId())
        {
            case R.id.button_use:
            {
                viewModel.selectAssetIdsToRedeem(this, ticket);
            }
            break;
            case R.id.button_sell:
            {
                viewModel.sellTicketRouter(this, ticket);// showSalesOrder(this, ticket);
            }
            break;
            case R.id.button_transfer:
            {
                viewModel.showTransferToken(this, ticket);
            }
            break;
        }
    }

    private void onTicketIdClick(View view, TicketRange range) {
        Context context = view.getContext();

        //TODO: Perform some action when token is clicked
    }
}