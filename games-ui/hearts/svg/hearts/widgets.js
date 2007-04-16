var svg_ns = "http://www.w3.org/2000/svg";
var widgets_ns = "http://games.staticcling.org/widgets/1.0"

// do a depth first search on a given tree fragment to find an element with
//  the given id. A nifty idea (if it's possible) would be to add a memberised
//  version of this function to the Node class, and make it available
//  (probably as getElementById) everywhere.
function findElementById(id, node) {
	// stopping case, bail if there are no children
	if (!node.hasChildNodes()) { 
		return null; 
	}

    var child = node.firstChild
    while (child != null) {
        // only press on if this is an element node
        if (child.nodeType == 1) {
            var result = findElementById(id, child);

            // other stopping case, we've found our result and just need to
            // unwind ourself to return it
    		if (result != null) {
                return result;
            }

            // if this node matches, return it, and cause some unwinding to happen
            if (child.getAttribute("id") == id) {
                return child;
            }
        }

        child = child.nextSibling
	}

    return null;
}

// goes through the steps for creating an simple event with the given name,
//  and returns it in a state where it's ready to be dispatched
function createEvent(name) {
    var evt = document.createEvent("Event");
    evt.initEvent(name, false, false);
    return evt;
}

/* widget class */
function Widget() {};
Widget.prototype = new Object();
Widget.prototype.cellpadding = 0.25;

// call the function provided on each node in the tree underneath 'nodes', 
//  during a depth first search.  If you want to have func called on the root 
//  node, make the call directly after calling tree_foreach.
Widget.prototype.tree_foreach = function(node, func) {
	// stopping case, bail if there are no children
	if (!node.hasChildNodes()) { 
		return; 
	}

    var child = node.firstChild
    while (child != null) {
        // only press on if this is an element node
        if (child.nodeType == 1) {
    		this.tree_foreach(child, func);
            func(child);
        }

        child = child.nextSibling
	}
}

// utility function to mangle the id of a node
function mangle_id(node, suffix) {
    var attr = node.getAttribute("id");
    if (attr != "") {
        attr += "-" + suffix;
        node.setAttribute("id", attr);
    }
}

/* selection list class */
SelectionList.prototype = new Widget;
SelectionList.prototype.constructor = SelectionList;

// This array ends up being rather important.  It determines which elements
// are sensitive to which events.  The format is 
//      element id : ["event1", "event2", ...]
// Because we're in the selectionlist widget, you can leave that part of the
// element id out (besides, JS doesn't allow - in identifiers).
SelectionList.prototype.events = {
    dropdown : ["click"]
};

// the constructor
function SelectionList(id, template_id) {
	this.id = id;
    this.expanded = 0;
    this.choices = [];

	// find the template asked for, and do a deep copy of it
	template = document.getElementById(template_id);
	clone = template.cloneNode(true); 

	// set the id of the base group to something standard, so that operations
	// can at least be done on the top-level group
	clone.setAttribute("id", "selectionlist-" + id); 

	// save the newly cloned tree so that it can be grafted into the document
	// in the place where the user wants eg.
	//   (node.appendChild(selectionlist.svgElement))
	this.svgElement = clone;

	// walk through the newly created svg tree, setting all of the element's
	// id attributes to something (hopefully) unique & meaningful
	this.tree_foreach(clone, function(node) { 
        mangle_id(node, id);
    });

    // configure all of the events that are handled by this widget
    for (id in this.events) {
        var element = findElementById("selectionlist-" + id + "-" + this.id, clone);
        if (element == null) { literalmessage(id + " null"); continue; }
        for (idx in this.events[id]) {
            element.addEventListener(this.events[id][idx], this, false);
        }
    }

    // cache a reference to this frequently used bit
    this._choices_list = findElementById("selectionlist-choices-list" + "-" + this.id, clone);

    // XXX temp... these need to be adjustable, and the functions to adjust
    // all of the sizes need to be overridable in subclasses so that different
    // decorations can be adjusted (rather than just square)
    this._widget_height = 4;
    this._widget_width = 22;
}

SelectionList.prototype.handleEvent = function(evt) {
    var element;
    var target = evt.getTarget();
    var target_id = target.getAttribute("id");

    // make sure that we have an id to compare against, but leave target unmolested
    var tmp_target = target;
    while (target_id == "") {
        tmp_target = tmp_target.parentNode;
        target_id = tmp_target.getAttribute("id");
    }

//    literalmessage("handling event " + evt.type + " for " + target_id);

    if (/^selectionlist-dropdown/.test(target_id)) {
        if (evt.type == "click") {
            if (this.expanded) {
                this.hideChoicesList();
                this.svgElement.dispatchEvent(createEvent("selection-not-changed"));
            } else {
                this.showChoicesList();
            }
        }
    } else if (/^selectionlist-choices-list/.test(target_id)) {
        if (evt.type == "click") {
            var element = findElementById("selectionlist-content" + "-" + this.id, this.svgElement);
            var index = target.getAttributeNS(widgets_ns, "index");
            // only take action if the value has changed
            if (this.getValue() != this.choices[index]) {
                this.showValue(this.choices[index]);
                this.svgElement.dispatchEvent(createEvent("selection-changed"));
            }

            this.hideChoicesList(); // always hide the choices on click
        }
    }
}

// this is a candidate for inclusion in the widget class, it proxies the
// addEventListener call through to the top-level svg element
SelectionList.prototype.addEventListener = function(type, listener, useCapture) {
    this.svgElement.addEventListener(type, listener, useCapture);
}

// this is a candidate for inclusion in the widget class, it proxies the
// removeEventListener call through to the top-level svg element
SelectionList.prototype.removeEventListener = function(type, listener, useCapture) {
    this.svgElement.removeEventListener(type, listener, useCapture);
}

SelectionList.prototype.showChoicesList = function() {
    var element = findElementById("selectionlist-choices" + "-" + this.id, this.svgElement);
    element.setAttribute("display", "block");
    this.expanded = 1;
}

SelectionList.prototype.hideChoicesList = function() {
    var element = findElementById("selectionlist-choices" + "-" + this.id, this.svgElement);
    element.setAttribute("display", "none");
    this.expanded = 0;
}

SelectionList.prototype.clearChoicesList = function() {
    var obj, ls;

    ls = this._choices_list.childNodes;
    while (ls.length > 0) {
        obj = ls.item(0);
        this._choices_list.removeChild(obj);
    }
}

SelectionList.prototype.updateChoicesList = function() {
    this.clearChoicesList();

    // adjust the decoration so that the choices below will fit -- XXX this should
    // probably be a function call so that it can be overridden by theme
    // creators
    var decoration = findElementById("selectionlist-choices-rect" + "-" + this.id, this.svgElement);
    decoration.setAttribute("height", this.choices.length * this._widget_height);

    // the general strategy here is that the selectionbox template will itself
    // contain a template for how the SVG for an individual choice should
    // look.  This lets cosmetic changes be made easily without changing the
    // code.  The important elements are the ...event-box... one (which
    // handles the clicks) and the ...content... one, which displays the text
    // (or whatever) that is the content that the user can see.  This template
    // is cloned, the id attributes are removed so they don't conflict, and
    // then it's added to the proper place
    var template = findElementById("selectionlist-choice-template" + "-" + this.id, this.svgElement);

    for (i = 0; i < this.choices.length; i++) {
        var content = template.cloneNode(true);

        var eventbox = findElementById("selectionlist-choice-event-box" + "-" + this.id, content);
        eventbox.setAttributeNS(widgets_ns, "index", i);
        eventbox.setAttribute("x", this.cellpadding);
        eventbox.setAttribute("width", this._widget_width - this.cellpadding * 2);
        eventbox.setAttribute("y", i * this._widget_height + (this.cellpadding / 2));
        eventbox.setAttribute("height", this._widget_height - this.cellpadding);
        eventbox.addEventListener("click", this, false); // handle clicks

        var text = findElementById("selectionlist-choice-content" + "-" + this.id, content);
        text.setAttribute("x", 1);
        text.setAttribute("y", (this._widget_height * 0.8) + this._widget_height * i);
        text.textContent = this.choices[i];

        // remove all of the id attributes, to avoid conflicts
        content.removeAttribute("id");
        this.tree_foreach(content, function(node) { 
            node.removeAttribute("id");
        });

        content.removeAttribute("display"); // show the group
        this._choices_list.appendChild(content);
    }
}

SelectionList.prototype.setChoices = function(choices) {
    this.choices = choices;
    this.updateChoicesList();
}

SelectionList.prototype.clearChoices = function(choices) {
    this.choices = [];
    this.updateChoicesList();
}

SelectionList.prototype.choiceAtIndex = function(index) {
    return this.choices[index];
}

// updates the content field without calling the onselect function
SelectionList.prototype.showValue = function(value) {
    // XXX temp, genericise this
    var element = findElementById("selectionlist-content" + "-" + this.id, this.svgElement);
    element.textContent = value; // XXX temporary
}

SelectionList.prototype.getValue = function() {
    // XXX temp, genericise this
    var element = findElementById("selectionlist-content" + "-" + this.id, this.svgElement);
    return element.textContent;
}

// this is one of the functions that will definitely have to be overridden to
// work with any template that employs decoration other than rectangles
//
// XXX presently the height parameter is ignored, and it's pretty bogus,
// because the width is only talking about the width of the widget less the
// dropdown button (which is a hardcoded 4 units wide)
//
// TODO Investigate doing this with DOM mutation events rather than with an
// explicit function.  This'd make things a whole lot more svg-esque and allow
// changes to be made indirectly via JS
SelectionList.prototype.setSize = function(width, height) {
    // save the new values
    this._widget_height = 4; // XXX height ignored (second warning)
    this._widget_width = width;

    // set the width of the collapsed mode decoration
    var element = findElementById("selectionlist-text-box" + "-" + this.id, this.svgElement);
    element.setAttribute("width", width);

    // set the location of the dropdown button
    element = findElementById("selectionlist-dropdown" + "-" + this.id, this.svgElement);
    element.setAttribute("transform", "translate(" + width + " 0)");

    // set the width of the expanded decoration
    element = findElementById("selectionlist-choices-rect" + "-" + this.id, this.svgElement);
    element.setAttribute("width", width);
    
    // adjust the choice template (if it needs it... not this one)

    // redraw the choices list
    this.updateChoicesList();
}

// vim: set ts=4 sw=4 et si
