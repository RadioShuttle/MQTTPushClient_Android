/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;


public abstract class Item {
    public Item() {
        id = cnt++;
    }

    public int id; // transient (for internal use)

    public int groupIdx;
    public int orderInGroup;
    public String label;

    public abstract int getType();

    public final static int TYPE_GROUP = 0;
    public final static int TYPE_TEXT = 1;

    public static class Comparator implements java.util.Comparator<Item> {

        @Override
        public int compare(Item o1, Item o2) {
            int cmp = 0;
            int g1 = o1 == null ? 0 : o1.groupIdx;
            int g2 = o2 == null ? 0 : o2.groupIdx;
            int s1 = o1 == null ? 0 : o1.orderInGroup;
            int s2 = o2 == null ? 0 : o2.orderInGroup;
            if (g1 < g2) {
                cmp = -1;
            } else if (g1 > g2) {
                cmp = 1;
            } else {
                if (s1 < s2) {
                    cmp = -1;
                } else if (s1 > s2) {
                    cmp = 1;
                }
            }
            return cmp;
        }
    }
    private static int cnt = 0;
}
