package org.schabi.newpipe.fragments.list.search.filter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.search.filter.FilterGroup;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.util.ServiceHelper;

import androidx.annotation.NonNull;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.core.view.MenuCompat;

import static android.content.ContentValues.TAG;

public class SearchFilterUI extends SearchFilterLogic {

    private final Context context;
    private final int searchIconRes;
    MenuItem groupNameItem = null;
    private Menu menu = null;

    public SearchFilterUI(final Callback callback, final Context context) {
        super(callback);
        this.searchIconRes = R.drawable.baseline_search_24;
        this.context = context;
    }


    @Override
    protected void createContentFilterPre() {
        menu.add(MENU_GROUP_SEARCH_BUTTON,
                        ITEM_IDENTIFIER_UNKNOWN,
                        0,
                        "search")
                .setEnabled(true)
                .setCheckable(false)
                .setIcon(searchIconRes);
    }

    protected void createContentFilterGroup(final FilterGroup contentGroup) {
        // here we set a named group separator
        // ->  this item should not be enabled and selectable
        groupNameItem = menu.add(
                MENU_GROUP_LAST_CONTENT_FILTER,
                ITEM_IDENTIFIER_UNKNOWN,
                0,
                contentGroup.groupName);
        groupNameItem.setEnabled(false);
        groupNameItem.setCheckable(false);

        menu.setGroupCheckable(MENU_GROUP_CONTENT_FILTER, true,
                contentGroup.onlyOneCheckable);
    }

    @Override
    protected void createContentFilterGroupElement(final FilterItem filter) {
        final MenuItem item = menu.add(MENU_GROUP_CONTENT_FILTER,
                filter.getIdentifier(),
                0,
                ServiceHelper.getTranslatedFilterString(filter.getName(), context));
        addContentFilterIdToItemMap(filter.getIdentifier(), new MenuItemUiWrapper(item));
    }

    @Override
    protected void createContentFilterGroupFinished(final FilterGroup contentGroup) {
        makeAllButFirstMenuItemInGroupCheckable(contentGroup, MENU_GROUP_CONTENT_FILTER);
    }

    @Override
    protected void createSortFilterGroup(final FilterGroup sortGroup, final int lastUsedGroupId) {
        groupNameItem = menu.add(
                lastUsedGroupId,
                sortGroup.identifier,
                0,
                sortGroup.groupName);
        groupNameItem.setEnabled(false);
        addToAllSortFilterItToItemMapWrapper(sortGroup.identifier, groupNameItem);
    }

    private void addToAllSortFilterItToItemMapWrapper(final int id,
                                                      final MenuItem item) {
        addToAllSortFilterItToItemMap(id, new MenuItemUiWrapper(item));
    }

    @Override
    protected void createSortFilterGroupElement(final FilterItem filter,
                                                final int lastUsedGroupId) {
        final MenuItem item = menu.add(lastUsedGroupId,
                filter.getIdentifier(),
                0,
                filter.getName()
                /* ServiceHelper.getTranslatedFilterString(filter, c)*/);
        addToAllSortFilterItToItemMapWrapper(filter.getIdentifier(), item);
    }

    @Override
    protected void createSortFilterGroupFinished(final FilterGroup sortGroup,
                                                 final int lastUsedGroupId) {
        makeAllButFirstMenuItemInGroupCheckable(sortGroup, lastUsedGroupId);
    }

    private void makeAllButFirstMenuItemInGroupCheckable(final FilterGroup group,
                                                         final int groupId) {
        menu.setGroupCheckable(groupId, true, group.onlyOneCheckable);
        // as setGroupCheckable() checkables for all items we have to manually
        // uncheckable for the name thingy
        if (groupNameItem != null) {
            groupNameItem.setCheckable(false);
        }
    }

    @SuppressLint("RestrictedApi")
    private void alwaysShowMenuItemIcon(final Menu theMenu) {
        // always show icons
        if (theMenu instanceof MenuBuilder) {
            final MenuBuilder builder = ((MenuBuilder) theMenu);
            builder.setOptionalIconsVisible(true);
        }
    }

    public void createSearchUI(@NonNull final Menu theMenu) {
        this.menu = theMenu;
        alwaysShowMenuItemIcon(theMenu);
        super.createFilterLogic();
        MenuCompat.setGroupDividerEnabled(theMenu, true);
    }

    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        if (item.getGroupId() == MENU_GROUP_SEARCH_BUTTON) { // catch the search button

            prepareForSearch();

        } else { // all other menu groups -> content filters and sort filters

            // main part for holding onto the menu -> not closing it
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
            item.setActionView(new View(context));
            item.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {

                @Override
                public boolean onMenuItemActionExpand(final MenuItem item) {
                    // catch the contentFilter
                    if (item.getGroupId() <= MENU_GROUP_LAST_CONTENT_FILTER) {
                        final int filterId = item.getItemId();
                        selectContentFilter(filterId);
                    } else { // the sort filters going here
                        Log.d(TAG, "onMenuItemActionExpand: sort filters are here");

                        selectSortFilter(item.getItemId());
                    }

                    return false;
                }

                @Override
                public boolean onMenuItemActionCollapse(final MenuItem item) {
                    return false;
                }
            });
        }

        return false;
    }

    private static class MenuItemUiWrapper implements UiItemWrapper {

        private final MenuItem item;

        MenuItemUiWrapper(final MenuItem item) {
            this.item = item;

        }

        @Override
        public void setVisible(final boolean visible) {
            item.setVisible(visible);
        }

        @Override
        public int getItemId() {
            return item.getItemId();
        }

        @Override
        public int getGroupId() {
            return item.getGroupId();
        }

        @Override
        public boolean isChecked() {
            return item.isChecked();
        }

        @Override
        public void setChecked(final boolean checked) {
            item.setChecked(checked);
        }
    }
}
