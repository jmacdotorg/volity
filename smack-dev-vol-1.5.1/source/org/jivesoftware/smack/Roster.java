/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright 2003-2004 Jive Software.
 *
 * All rights reserved. Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.smack;

import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.filter.*;
import org.jivesoftware.smack.util.StringUtils;

import java.util.*;

/**
 * Represents a user's roster, which is the collection of users a person receives
 * presence updates for. Roster items are categorized into groups for easier management.<p>
 *
 * Others users may attempt to subscribe to this user using a subscription request. Three
 * modes are supported for handling these requests: <ul>
 *      <li> SUBSCRIPTION_ACCEPT_ALL -- accept all subscription requests.
 *      <li> SUBSCRIPTION_REJECT_ALL -- reject all subscription requests.
 *      <li> SUBSCRIPTION_MANUAL -- manually process all subscription requests. </ul>
 *
 * @see XMPPConnection#getRoster()
 * @author Matt Tucker
 */
public class Roster {

    /**
     * Automatically accept all subscription requests. This is the default mode
     * and is suitable for simple client. More complex client will likely wish to
     * handle subscription requests manually.
     */
    public static final int SUBSCRIPTION_ACCEPT_ALL = 0;

    /**
     * Automatically reject all subscription requests.
     */
    public static final int SUBSCRIPTION_REJECT_ALL = 1;

    /**
     * Subscription requests are ignored, which means they must be manually
     * processed by registering a listener for presence packets and then looking
     * for any presence requests that have the type Presence.Type.SUBSCRIBE.
     */
    public static final int SUBSCRIPTION_MANUAL = 2;

    /**
     * The default subscription processing mode to use when a Roster is created. By default 
     * all subscription requests are automatically accepted. 
     */
    private static int defaultSubscriptionMode = SUBSCRIPTION_ACCEPT_ALL;

    private XMPPConnection connection;
    private Map groups;
    private List entries;
    private List unfiledEntries;
    private List rosterListeners;
    private Map presenceMap;
    // The roster is marked as initialized when at least a single roster packet
    // has been recieved and processed.
    boolean rosterInitialized = false;

    private int subscriptionMode = getDefaultSubscriptionMode();

    /**
     * Returns the default subscription processing mode to use when a new Roster is created. The 
     * subscription processing mode dictates what action Smack will take when subscription 
     * requests from other users are made. The default subscription mode 
     * is {@link #SUBSCRIPTION_ACCEPT_ALL}.
     * 
     * @return the default subscription mode to use for new Rosters
     */
    public static int getDefaultSubscriptionMode() {
        return defaultSubscriptionMode;
    }

    /**
     * Sets the default subscription processing mode to use when a new Roster is created. The 
     * subscription processing mode dictates what action Smack will take when subscription 
     * requests from other users are made. The default subscription mode 
     * is {@link #SUBSCRIPTION_ACCEPT_ALL}.
     *
     * @param subscriptionMode the default subscription mode to use for new Rosters.
     */
    public static void setDefaultSubscriptionMode(int subscriptionMode) {
        defaultSubscriptionMode = subscriptionMode;
    }

    /**
     * Creates a new roster.
     *
     * @param connection an XMPP connection.
     */
    Roster(final XMPPConnection connection) {
        this.connection = connection;
        groups = new Hashtable();
        unfiledEntries = new ArrayList();
        entries = new ArrayList();
        rosterListeners = new ArrayList();
        presenceMap = new HashMap();
        // Listen for any roster packets.
        PacketFilter rosterFilter = new PacketTypeFilter(RosterPacket.class);
        connection.addPacketListener(new RosterPacketListener(), rosterFilter);
        // Listen for any presence packets.
        PacketFilter presenceFilter = new PacketTypeFilter(Presence.class);
        connection.addPacketListener(new PresencePacketListener(), presenceFilter);
    }

    /**
     * Returns the subscription processing mode, which dictates what action
     * Smack will take when subscription requests from other users are made.
     * The default subscription mode is {@link #SUBSCRIPTION_ACCEPT_ALL}.<p>
     *
     * If using the manual mode, a PacketListener should be registered that
     * listens for Presence packets that have a type of
     * {@link org.jivesoftware.smack.packet.Presence.Type#SUBSCRIBE}.
     *
     * @return the subscription mode.
     */
    public int getSubscriptionMode() {
        return subscriptionMode;
    }

    /**
     * Sets the subscription processing mode, which dictates what action
     * Smack will take when subscription requests from other users are made.
     * The default subscription mode is {@link #SUBSCRIPTION_ACCEPT_ALL}.<p>
     *
     * If using the manual mode, a PacketListener should be registered that
     * listens for Presence packets that have a type of
     * {@link org.jivesoftware.smack.packet.Presence.Type#SUBSCRIBE}.
     *
     * @param subscriptionMode the subscription mode.
     */
    public void setSubscriptionMode(int subscriptionMode) {
        if (subscriptionMode != SUBSCRIPTION_ACCEPT_ALL &&
                subscriptionMode != SUBSCRIPTION_REJECT_ALL &&
                subscriptionMode != SUBSCRIPTION_MANUAL)
        {
            throw new IllegalArgumentException("Invalid mode.");
        }
        this.subscriptionMode = subscriptionMode;
    }

    /**
     * Reloads the entire roster from the server. This is an asynchronous operation,
     * which means the method will return immediately, and the roster will be
     * reloaded at a later point when the server responds to the reload request.
     */
    public void reload() {
        connection.sendPacket(new RosterPacket());
    }

    /**
     * Adds a listener to this roster. The listener will be fired anytime one or more
     * changes to the roster are pushed from the server.
     *
     * @param rosterListener a roster listener.
     */
    public void addRosterListener(RosterListener rosterListener) {
        synchronized (rosterListeners) {
            if (!rosterListeners.contains(rosterListener)) {
                rosterListeners.add(rosterListener);
            }
        }
    }

    /**
     * Removes a listener from this roster. The listener will be fired anytime one or more
     * changes to the roster are pushed from the server.
     *
     * @param rosterListener a roster listener.
     */
    public void removeRosterListener(RosterListener rosterListener) {
        synchronized (rosterListeners) {
            rosterListeners.remove(rosterListener);
        }
    }

    /**
     * Creates a new group.<p>
     *
     * Note: you must add at least one entry to the group for the group to be kept
     * after a logout/login. This is due to the way that XMPP stores group information.
     *
     * @param name the name of the group.
     * @return a new group.
     */
    public RosterGroup createGroup(String name) {
        synchronized (groups) {
            if (groups.containsKey(name)) {
                throw new IllegalArgumentException("Group with name " + name + " alread exists.");
            }
            RosterGroup group = new RosterGroup(name, connection);
            groups.put(name, group);
            return group;
        }
    }

    /**
     * Creates a new roster entry and presence subscription. The server will asynchronously
     * update the roster with the subscription status.
     *
     * @param user the user. (e.g. johndoe@jabber.org)
     * @param name the nickname of the user.
     * @param groups the list of group names the entry will belong to, or <tt>null</tt> if the
     *      the roster entry won't belong to a group.
     */
    public void createEntry(String user, String name, String [] groups) throws XMPPException {
        // Create and send roster entry creation packet.
        RosterPacket rosterPacket = new RosterPacket();
        rosterPacket.setType(IQ.Type.SET);
        RosterPacket.Item item = new RosterPacket.Item(user, name);
        if (groups != null) {
            for (int i=0; i<groups.length; i++) {
                if (groups[i] != null) {
                    item.addGroupName(groups[i]);
                }
            }
        }
        rosterPacket.addRosterItem(item);
        // Wait up to a certain number of seconds for a reply from the server.
        PacketCollector collector = connection.createPacketCollector(
                new PacketIDFilter(rosterPacket.getPacketID()));
        connection.sendPacket(rosterPacket);
        IQ response = (IQ)collector.nextResult(SmackConfiguration.getPacketReplyTimeout());
        collector.cancel();
        if (response == null) {
            throw new XMPPException("No response from the server.");
        }
        // If the server replied with an error, throw an exception.
        else if (response.getType() == IQ.Type.ERROR) {
            throw new XMPPException(response.getError());
        }

        // Create a presence subscription packet and send.
        Presence presencePacket = new Presence(Presence.Type.SUBSCRIBE);
        presencePacket.setTo(user);
        connection.sendPacket(presencePacket);
    }

    /**
     * Removes a roster entry from the roster. The roster entry will also be removed from the 
     * unfiled entries or from any roster group where it could belong and will no longer be part
     * of the roster. Note that this is an asynchronous call -- Smack must wait for the server
     * to send an updated subscription status.
     *
     * @param entry a roster entry.
     */
    public void removeEntry(RosterEntry entry) throws XMPPException {
        // Only remove the entry if it's in the entry list.
        // The actual removal logic takes place in RosterPacketListenerprocess>>Packet(Packet)
        synchronized (entries) {
            if (!entries.contains(entry)) {
                return;
            }
        }
        RosterPacket packet = new RosterPacket();
        packet.setType(IQ.Type.SET);
        RosterPacket.Item item = RosterEntry.toRosterItem(entry);
        // Set the item type as REMOVE so that the server will delete the entry
        item.setItemType(RosterPacket.ItemType.REMOVE);
        packet.addRosterItem(item);
        PacketCollector collector = connection.createPacketCollector(
        new PacketIDFilter(packet.getPacketID()));
        connection.sendPacket(packet);
        IQ response = (IQ)collector.nextResult(SmackConfiguration.getPacketReplyTimeout());
        collector.cancel();
        if (response == null) {
            throw new XMPPException("No response from the server.");
        }
        // If the server replied with an error, throw an exception.
        else if (response.getType() == IQ.Type.ERROR) {
            throw new XMPPException(response.getError());
        }
        else {
            
        }
    }

    /**
     * Returns a count of the entries in the roster.
     *
     * @return the number of entries in the roster.
     */
    public int getEntryCount() {
        HashMap entryMap = new HashMap();
        // Loop through all roster groups.
        for (Iterator groups = getGroups(); groups.hasNext(); ) {
            RosterGroup rosterGroup = (RosterGroup) groups.next();
            for (Iterator entries = rosterGroup.getEntries(); entries.hasNext(); ) {
                entryMap.put(entries.next(), "");
            }
        }
        synchronized (unfiledEntries) {
            return entryMap.size() + unfiledEntries.size();
        }
    }

    /**
     * Returns all entries in the roster, including entries that don't belong to
     * any groups.
     *
     * @return all entries in the roster.
     */
    public Iterator getEntries() {
        ArrayList allEntries = new ArrayList();
        // Loop through all roster groups and add their entries to the answer
        for (Iterator groups = getGroups(); groups.hasNext(); ) {
            RosterGroup rosterGroup = (RosterGroup) groups.next();
            for (Iterator entries = rosterGroup.getEntries(); entries.hasNext(); ) {
                RosterEntry entry = (RosterEntry)entries.next();
                if (!allEntries.contains(entry)) {
                    allEntries.add(entry);
                }
            }
        }
        // Add the roster unfiled entries to the answer
        synchronized (unfiledEntries) {
            allEntries.addAll(unfiledEntries);
        }
        return allEntries.iterator();
    }

    /**
     * Returns a count of the unfiled entries in the roster. An unfiled entry is
     * an entry that doesn't belong to any groups.
     *
     * @return the number of unfiled entries in the roster.
     */
    public int getUnfiledEntryCount() {
        synchronized (unfiledEntries) {
            return unfiledEntries.size();
        }
    }

    /**
     * Returns an Iterator for the unfiled roster entries. An unfiled entry is
     * an entry that doesn't belong to any groups.
     *
     * @return an iterator the unfiled roster entries.
     */
    public Iterator getUnfiledEntries() {
        synchronized (unfiledEntries) {
            return Collections.unmodifiableList(new ArrayList(unfiledEntries)).iterator();
        }
    }

    /**
     * Returns the roster entry associated with the given XMPP address or
     * <tt>null</tt> if the user is not an entry in the roster.
     *
     * @param user the XMPP address of the user (eg "jsmith@example.com"). The address could be
     * in any valid format (e.g. "domain/resource", "user@domain" or "user@domain/resource").
     * @return the roster entry or <tt>null</tt> if it does not exist.
     */
    public RosterEntry getEntry(String user) {
        if (user == null) {
            return null;
        }
        synchronized (entries) {
            for (Iterator i=entries.iterator(); i.hasNext(); ) {
                RosterEntry entry = (RosterEntry)i.next();
                if (entry.getUser().toLowerCase().equals(user.toLowerCase())) {
                    return entry;
                }
            }
        }
        return null;
    }

    /**
     * Returns true if the specified XMPP address is an entry in the roster.
     *
     * @param user the XMPP address of the user (eg "jsmith@example.com"). The address could be
     * in any valid format (e.g. "domain/resource", "user@domain" or "user@domain/resource").
     * @return true if the XMPP address is an entry in the roster.
     */
    public boolean contains(String user) {
        if (user == null) {
            return false;
        }
        synchronized (entries) {
            for (Iterator i=entries.iterator(); i.hasNext(); ) {
                RosterEntry entry = (RosterEntry)i.next();
                if (entry.getUser().toLowerCase().equals(user.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns the roster group with the specified name, or <tt>null</tt> if the
     * group doesn't exist.
     *
     * @param name the name of the group.
     * @return the roster group with the specified name.
     */
    public RosterGroup getGroup(String name) {
        synchronized (groups) {
            return (RosterGroup)groups.get(name);
        }
    }

    /**
     * Returns the number of the groups in the roster.
     *
     * @return the number of groups in the roster.
     */
    public int getGroupCount() {
        synchronized (groups) {
            return groups.size();
        }
    }

    /**
     * Returns an iterator the for all the roster groups.
     *
     * @return an iterator for all roster groups.
     */
    public Iterator getGroups() {
        synchronized (groups) {
            List groupsList = Collections.unmodifiableList(new ArrayList(groups.values()));
            return groupsList.iterator();
        }
    }

    /**
     * Returns the presence info for a particular user, or <tt>null</tt> if the user
     * is unavailable (offline) or if no presence information is available, such as
     * when you are not subscribed to the user's presence updates.<p>
     * 
     * If the user has several presences (one for each resource) then answer the presence
     * with the highest priority.
     *
     * @param user a fully qualified xmpp ID. The address could be in any valid format (e.g.
     * "domain/resource", "user@domain" or "user@domain/resource").
     * @return the user's current presence, or <tt>null</tt> if the user is unavailable
     *      or if no presence information is available..
     */
    public Presence getPresence(String user) {
        String key = getPresenceMapKey(user);
        Map userPresences = (Map) presenceMap.get(key);
        if (userPresences == null) {
            return null;
        }
        else {
            // Find the resource with the highest priority
            // Might be changed to use the resource with the highest availability instead.
            Iterator it = userPresences.keySet().iterator();
            Presence p;
            Presence presence = null;

            while (it.hasNext()) {
                p = (Presence) userPresences.get(it.next());
                if (presence == null) {
                    presence = p;
                }
                else {
                    if (p.getPriority() > presence.getPriority()) {
                        presence = p;
                    }
                }
            }
            return presence;
        }
    }

    /**                                                                                                    
     * Returns the presence info for a particular user's resource, or <tt>null</tt> if the user
     * is unavailable (offline) or if no presence information is available, such as
     * when you are not subscribed to the user's presence updates.
     *
     * @param userResource a fully qualified xmpp ID including a resource.
     * @return the user's current presence, or <tt>null</tt> if the user is unavailable 
     * or if no presence information is available.
     */
    public Presence getPresenceResource(String userResource) {
        String key = getPresenceMapKey(userResource);
        String resource = StringUtils.parseResource(userResource);
        Map userPresences = (Map)presenceMap.get(key);
        if (userPresences == null) {
            return null;
        }
        else {
            return (Presence) userPresences.get(resource);
        }
    }

    /**
     * Returns an iterator (of Presence objects) for all the user's current presences
     * or <tt>null</tt> if the user is unavailable (offline) or if no presence information
     * is available, such as when you are not subscribed to the user's presence updates.
     *
     * @param user a fully qualified xmpp ID, e.g. jdoe@example.com
     * @return an iterator (of Presence objects) for all the user's current presences,
     *      or <tt>null</tt> if the user is unavailable or if no presence information
     *      is available.
     */
    public Iterator getPresences(String user) {
        String key = getPresenceMapKey(user);
        Map userPresences = (Map)presenceMap.get(key);
        if (userPresences == null) {
            return null;
        }
        else {
            synchronized (userPresences) {
                return new HashMap(userPresences).values().iterator();
            }
        }
    }

    /**
     * Returns the key to use in the presenceMap for a fully qualified xmpp ID. The roster
     * can contain any valid address format such us "domain/resource", "user@domain" or
     * "user@domain/resource". If the roster contains an entry associated with the fully qualified
     * xmpp ID then use the fully qualified xmpp ID as the key in presenceMap, otherwise use the
     * bare address. Note: When the key in presenceMap is a fully qualified xmpp ID, the
     * userPresences is useless since it will always contain one entry for the user.
     *
     * @param user the fully qualified xmpp ID, e.g. jdoe@example.com/Work.
     * @return the key to use in the presenceMap for the fully qualified xmpp ID.
     */
    private String getPresenceMapKey(String user) {
        String key = user;
        if (!contains(user)) {
            key = StringUtils.parseBareAddress(user);
        }
        return key;
    }

    /**
     * Fires roster changed event to roster listeners.
     */
    private void fireRosterChangedEvent() {
        RosterListener [] listeners = null;
        synchronized (rosterListeners) {
            listeners = new RosterListener[rosterListeners.size()];
            rosterListeners.toArray(listeners);
        }
        for (int i=0; i<listeners.length; i++) {
            listeners[i].rosterModified();
        }
    }

    /**
     * Fires roster presence changed event to roster listeners.
     */
    private void fireRosterPresenceEvent(String user) {
        RosterListener [] listeners = null;
        synchronized (rosterListeners) {
            listeners = new RosterListener[rosterListeners.size()];
            rosterListeners.toArray(listeners);
        }
        for (int i=0; i<listeners.length; i++) {
            listeners[i].presenceChanged(user);
        }
    }

    /**
     * Listens for all presence packets and processes them.
     */
    private class PresencePacketListener implements PacketListener {
        public void processPacket(Packet packet) {
            Presence presence = (Presence)packet;
            String from = presence.getFrom();
            String key = getPresenceMapKey(from);

            // If an "available" packet, add it to the presence map. Each presence map will hold
            // for a particular user a map with the presence packets saved for each resource.
            if (presence.getType() == Presence.Type.AVAILABLE) {
                Map userPresences;
                // Get the user presence map
                if (presenceMap.get(key) == null) {
                    userPresences = new HashMap();
                    presenceMap.put(key, userPresences);
                }
                else {
                    userPresences = (Map)presenceMap.get(key);
                }
                // Add the new presence, using the resources as a key.
                synchronized (userPresences) {
                    userPresences.put(StringUtils.parseResource(from), presence);
                }
                // If the user is in the roster, fire an event.
                synchronized (entries) {
                    for (Iterator i = entries.iterator(); i.hasNext();) {
                        RosterEntry entry = (RosterEntry) i.next();
                        if (entry.getUser().toLowerCase().equals(key.toLowerCase())) {
                            fireRosterPresenceEvent(from);
                        }
                    }
                }
            }
            // If an "unavailable" packet, remove any entries in the presence map.
            else if (presence.getType() == Presence.Type.UNAVAILABLE) {
                if (presenceMap.get(key) != null) {
                    Map userPresences = (Map) presenceMap.get(key);
                    synchronized (userPresences) {
                        userPresences.remove(StringUtils.parseResource(from));
                    }
                    if (userPresences.isEmpty()) {
                        presenceMap.remove(key);
                    }
                }
                // If the user is in the roster, fire an event.
                synchronized (entries) {
                    for (Iterator i=entries.iterator(); i.hasNext(); ) {
                        RosterEntry entry = (RosterEntry)i.next();
                        if (entry.getUser().toLowerCase().equals(key.toLowerCase())) {
                            fireRosterPresenceEvent(from);
                        }
                    }
                }
            }
            else if (presence.getType() == Presence.Type.SUBSCRIBE) {
                if (subscriptionMode == SUBSCRIPTION_ACCEPT_ALL) {
                    // Accept all subscription requests.
                    Presence response = new Presence(Presence.Type.SUBSCRIBED);
                    response.setTo(presence.getFrom());
                    connection.sendPacket(response);
                }
                else if (subscriptionMode == SUBSCRIPTION_REJECT_ALL) {
                    // Reject all subscription requests.
                    Presence response = new Presence(Presence.Type.UNSUBSCRIBED);
                    response.setTo(presence.getFrom());
                    connection.sendPacket(response);
                }
                // Otherwise, in manual mode so ignore.
            }
        }
    }

    /**
     * Listens for all roster packets and processes them.
     */
    private class RosterPacketListener implements PacketListener {

        public void processPacket(Packet packet) {
            RosterPacket rosterPacket = (RosterPacket)packet;
            for (Iterator i=rosterPacket.getRosterItems(); i.hasNext(); ) {
                RosterPacket.Item item = (RosterPacket.Item)i.next();
                RosterEntry entry = new RosterEntry(item.getUser(), item.getName(),
                        item.getItemType(), connection);

                // If the packet is of the type REMOVE then remove the entry
                if (RosterPacket.ItemType.REMOVE.equals(item.getItemType())) {
                    // Remove the entry from the entry list.
                    if (entries.contains(entry)) {
                        entries.remove(entry);
                    }
                    // Remove the entry from the unfiled entry list.
                    synchronized (unfiledEntries) {
                        if (unfiledEntries.contains(entry)) {
                            unfiledEntries.remove(entry);
                        }
                    }
                    // Removing the user from the roster, so remove any presence information
                    // about them.
                    String key = StringUtils.parseName(item.getUser()) + "@" +
                            StringUtils.parseServer(item.getUser());
                    presenceMap.remove(key);
                }
                else {
                    // Make sure the entry is in the entry list.
                    if (!entries.contains(entry)) {
                        entries.add(entry);
                    }
                    else {
                        // If the entry was in then list then update its state with the new values
                        RosterEntry existingEntry =
                            (RosterEntry) entries.get(entries.indexOf(entry));
                        existingEntry.updateState(entry.getName(), entry.getType());
                    }
                    // If the roster entry belongs to any groups, remove it from the
                    // list of unfiled entries.
                    if (item.getGroupNames().hasNext()) {
                        synchronized (unfiledEntries) {
                            unfiledEntries.remove(entry);
                        }
                    }
                    // Otherwise add it to the list of unfiled entries.
                    else {
                        synchronized (unfiledEntries) {
                            if (!unfiledEntries.contains(entry)) {
                                unfiledEntries.add(entry);
                            }
                        }
                    }
                }

                // Find the list of groups that the user currently belongs to.
                List currentGroupNames = new ArrayList();
                for (Iterator j = entry.getGroups(); j.hasNext();  ) {
                    RosterGroup group = (RosterGroup)j.next();
                    currentGroupNames.add(group.getName());
                }

                // If the packet is not of the type REMOVE then add the entry to the groups
                if (!RosterPacket.ItemType.REMOVE.equals(item.getItemType())) {
                    // Create the new list of groups the user belongs to.
                    List newGroupNames = new ArrayList();
                    for (Iterator k = item.getGroupNames(); k.hasNext();  ) {
                        String groupName = (String)k.next();
                        // Add the group name to the list.
                        newGroupNames.add(groupName);

                        // Add the entry to the group.
                        RosterGroup group = getGroup(groupName);
                        if (group == null) {
                            group = createGroup(groupName);
                            groups.put(groupName, group);
                        }
                        // Add the entry.
                        group.addEntryLocal(entry);
                    }

                    // We have the list of old and new group names. We now need to
                    // remove the entry from the all the groups it may no longer belong
                    // to. We do this by subracting the new group set from the old.
                    for (int m=0; m<newGroupNames.size(); m++) {
                        currentGroupNames.remove(newGroupNames.get(m));
                    }
                }

                // Loop through any groups that remain and remove the entries.
                // This is neccessary for the case of remote entry removals.
                for (int n=0; n<currentGroupNames.size(); n++) {
                    String groupName = (String)currentGroupNames.get(n);
                    RosterGroup group = getGroup(groupName);
                    group.removeEntryLocal(entry);
                    if (group.getEntryCount() == 0) {
                        synchronized (groups) {
                            groups.remove(groupName);
                        }
                    }
                }
                // Remove all the groups with no entries. We have to do this because 
                // RosterGroup.removeEntry removes the entry immediately (locally) and the 
                // group could remain empty. 
                // TODO Check the performance/logic for rosters with large number of groups 
                for (Iterator it = getGroups(); it.hasNext();) {
                    RosterGroup group = (RosterGroup)it.next();
                    if (group.getEntryCount() == 0) {
                        synchronized (groups) {
                            groups.remove(group.getName());
                        }                            
                    }
                }
            }

            // Mark the roster as initialized.
            synchronized (Roster.this) {
                rosterInitialized = true;
                Roster.this.notifyAll();
            }

            // Fire event for roster listeners.
            fireRosterChangedEvent();
        }
    }
}