package com.retail.trucksales;

import android.view.View;

/** Base for the four tabs. */
abstract class Screen {
    final MainActivity a;
    final Db db;

    Screen(MainActivity a) { this.a = a; this.db = Db.get(a); }

    abstract View build();

    boolean dirty() { return false; }
}
