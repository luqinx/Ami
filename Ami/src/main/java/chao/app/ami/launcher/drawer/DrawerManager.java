package chao.app.ami.launcher.drawer;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Set;

import chao.app.ami.ActivitiesLifeCycleAdapter;
import chao.app.ami.Ami;
import chao.app.ami.UI;
import chao.app.debug.R;


public class DrawerManager implements DrawerXmlParser.DrawerXmlParserListener, View.OnClickListener, WindowCallbackHook.DispatchKeyEventListener {

    private static DrawerManager sDrawerManager;

    private RecyclerView mDrawerListView;
    private DrawerAdapter mDrawerAdapter;

    private DrawerNode mDrawerRootNode;

    private DrawerLayout mDrawerLayout;

    private ImageView mNavigationBackView;
    private TextView mNavigationPathView;

    private int mDrawerId;

    private FrameLayout mRealContent;
    private View mRealView;
    private ViewGroup mDecorView;

    private WeakReference<Context> mContext;

    private Application mApp;

    DrawerManager(Application app) {
        mApp = app;
    }


    private void setupView(Activity activity) {
        FrameLayout decorView = (FrameLayout) activity.findViewById(android.R.id.content);
        int decorViewChildCount = decorView.getChildCount();
        if (decorViewChildCount == 0) {
            return;
        }
        if (mDecorView == decorView) {
            return;
        }
        FrameLayout realView = new FrameLayout(activity);
        realView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View[] decorChildren = new View[decorViewChildCount];
        for (int i=0; i<decorViewChildCount; i++) {
            decorChildren[i] = decorView.getChildAt(i);
        }
        decorView.removeAllViews();
        for (int i=0;i<decorViewChildCount;i++) {
            View child = decorChildren[i];
            if (child == null) {
                continue;
            }
            realView.addView(child);
        }

        if (mDecorView != null) {
            mRealContent.removeView(mRealView);
            mDecorView.removeAllViews();
            if (mRealView != null) {
                mDecorView.addView(mRealView);
            }
        }

        mDecorView = decorView;
        mRealView = realView;
        mContext = new WeakReference<>(mDecorView.getContext());

        if (mDrawerLayout == null) {
            LayoutInflater inflater = LayoutInflater.from(mContext.get());
            mDrawerLayout = (DrawerLayout) inflater.inflate(R.layout.drawer_launcher, mDecorView, false);

            mNavigationBackView = findViewById(R.id.navigation_back);
            mNavigationBackView.setOnClickListener(this);
            mNavigationPathView = findViewById(R.id.navigation_title);

            mRealContent = findViewById(R.id.real_content);
            mDrawerListView = findViewById(R.id.ui_list);
            mDrawerListView.setLayoutManager(new LinearLayoutManager(mContext.get(), LinearLayoutManager.VERTICAL, false));
            mDrawerListView.addItemDecoration(new DividerItemDecoration(mContext.get(), LinearLayoutManager.VERTICAL));


            DrawerXmlParser parser = new DrawerXmlParser();
            parser.parseDrawer(mContext.get().getResources().openRawResource(mDrawerId), this);
        }
        mDecorView.addView(mDrawerLayout);
        mRealContent.addView(mRealView);
    }

    private <T extends View> T findViewById(int resId) {
        return (T) mDrawerLayout.findViewById(resId);
    }

    private void setDrawerId(int rawId) {
        mDrawerId = rawId;
    }

    @Override
    public void onXmlParserDone(DrawerNode rootNode) {
        mDrawerRootNode = rootNode;
        mDrawerAdapter = new DrawerAdapter(mDrawerRootNode);
        mDrawerListView.setAdapter(mDrawerAdapter);
    }

    @Override
    public void onXmlParserFailed(Exception e) {

    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.navigation_back) {
            mDrawerAdapter.navigationUp();
        }
    }

    @Override
    public boolean onDispatchKeyEvent(KeyEvent keyEvent) {
        if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
            if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
                mDrawerLayout.closeDrawers();
                return true;
            }
        }
        return false;
    }

    private class DrawerAdapter extends RecyclerView.Adapter {

        private NodeGroup mCurrentGroup;

        private DrawerAdapter(NodeGroup groupNode) {
            mCurrentGroup = groupNode;
            updateNavigation();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new RecyclerView.ViewHolder(LayoutInflater.from(mContext.get()).inflate(R.layout.simpel_drawer_item_layout, parent, false)) {};
        }

        @Override
        public void onBindViewHolder(final RecyclerView.ViewHolder holder, int position) {
            View itemView = holder.itemView;
            TextView textView = (TextView) itemView.findViewById(R.id.drawer_item_name);
            ImageView arrow = (ImageView) itemView.findViewById(R.id.drawer_item_arrow);
            Node node = mCurrentGroup.getChild(position);
            int visible = View.INVISIBLE;
            if (node instanceof NodeGroup) {
                visible = View.VISIBLE;
            }
            arrow.setVisibility(visible);
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Node node = mCurrentGroup.getChild(holder.getAdapterPosition());
                    if (node instanceof NodeGroup) {
                        NodeGroup group = (NodeGroup) node;
                        mDrawerAdapter.navigationTo(group);
                    } else if (node instanceof ComponentNode){
                        ComponentNode componentNode = (ComponentNode) node;
                        try {
                            mDrawerLayout.closeDrawer(GravityCompat.START, false);
                            Class<?> clazz = componentName(componentNode);
                            UI.show(mContext.get(), clazz, componentNode.getBundle(), componentNode.getFlags());
                        } catch (ClassNotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
            textView.setText(mCurrentGroup.getChild(position).getName());
        }

        private Class componentName(ComponentNode node) throws ClassNotFoundException {
            String packageName = mDrawerRootNode.getPackageName();
            String component = node.getComponent();
            if (component.charAt(0) == '.') {
                component = packageName + component;
            }
            return Class.forName(component);
        }

        @Override
        public int getItemCount() {
            return mCurrentGroup.size();
        }

        private void navigationUp() {
            NodeGroup group = mCurrentGroup.getParent();
            if (group != null) {
                mCurrentGroup = group;
                notifyDataSetChanged();
                updateNavigation();
            }
        }

        private void navigationTo(NodeGroup groupNode) {
            if (groupNode == null) {
                return;
            }
            mCurrentGroup = groupNode;
            notifyDataSetChanged();
            updateNavigation();
        }

        private void updateNavigation() {
            if (mCurrentGroup.getParent() != null) {
                mNavigationBackView.setImageResource(R.drawable.ic_navigation);
            } else {
                mNavigationBackView.setImageResource(0);
            }
            mNavigationPathView.setText(buildNavigationTitle(mCurrentGroup));
        }

        private String buildNavigationTitle(Node node) {
            if (node == null) {
                return "/";
            }
            String title = node.getName();
            if (title == null) {
                title = "";
            }
            NodeGroup parent = node.getParent();
            while (parent != null) {
                String parentName = parent.getName();
                if (!TextUtils.isEmpty(parentName)) {
                    title = parent.getName() + "/" +  title;
                }
                parent = parent.getParent();
            }

            return title;
        }

    }

    public static void init(Application app, int drawerXml) {
        sDrawerManager = new DrawerManager(app);
        sDrawerManager.setDrawerId(drawerXml);
        app.registerActivityLifecycleCallbacks(new ActivitiesLifeCycleAdapter(){

            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                activity.getWindow().setCallback(WindowCallbackHook.newInstance(activity, sDrawerManager));
            }

            @Override
            public void onActivityStarted(Activity activity) {
                sDrawerManager.setupView(activity);
            }

            @Override
            public void onActivityResumed(Activity activity) {
                sDrawerManager.injectInput(activity);
//                FrameProcessor.process(activity);
            }
        });
    }

    private void injectInput(Activity activity) {
        Intent intent = activity.getIntent();
        if (intent == null) {
            return;
        }
        Map<String, String> inputs = (Map<String, String>) intent.getSerializableExtra(Constants.EXTRA_KEY_INPUT);
        if (inputs == null) {
            return;
        }
        Set<String> viewIds = inputs.keySet();
        if (viewIds.size() == 0) {
            return;
        }
        for (String viewId : viewIds) {
            int resId = activity.getResources().getIdentifier(viewId, "id", Ami.getApp().getPackageName());
            View view = activity.findViewById(resId);
            if (view == null || !(view instanceof TextView)) {
                return;
            }
            ((TextView) view).setText(inputs.get(viewId));
        }
    }

    public static DrawerManager get() {
        if (sDrawerManager == null) {
            throw new NullPointerException("DrawerManager not initialization.");
        }
        return sDrawerManager;
    }

}