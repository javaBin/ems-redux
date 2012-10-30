/*
 All model properties should be treated as immutable data structures.
 */
_.templateSettings = {
    evaluate:/\[%([\s\S]+?)%\]/g,
    interpolate:/\[%=([\s\S]+?)%\]/g,
    escape:/\[%-([\s\S]+?)%\]/g
};

function parseItem(item) {
    var i = {};
    i.data = item.toObject();
    i.href = item.href;
    i.links = item.links;
    return i;
}

var Item = Backbone.Model.extend({
    idAttribute:"href",
    parse:parseItem
});


var CollectionJSON = Backbone.Collection.extend({
    model:Item,
    parse:function (coll) {
        var items = fromObject(coll).collection.items;
        console.log(items);
        return items;
    }
});

var Session = Item.extend({
});

var SessionCollection = CollectionJSON.extend({
    model:Session,
    comparator:function (s) {
        return s.title;
    }
});

var Event = Item.extend({
    parse:function (item) {
        var i = parseItem(item);
        i.data.start = cake.date.parse(i.data.start);
        i.data.end = cake.date.parse(i.data.end);
        i.sessionHref = item.findLinkByRel("session collection").href;
        /*var roomHref = item.findLinkByRel("room collection").href;
         var slotHref = item.findLinkByRel("slot collection").href;
         var rooms = new CollectionJSON();
         rooms.fetch({url: roomHref});
         i.rooms = rooms;
         var slots = new CollectionJSON();
         slots.fetch({url: slotHref});
         i.rooms = slots;*/
        return i;
    }
});

var EventCollection = CollectionJSON.extend({
    model:Event,
    comparator:function (e) {
        return e.name;
    }
});

var EventView = Backbone.View.extend({
    template:_.template($('#event-template').html()),
    render:function () {
        console.log(this.model.toJSON());
        this.$el.html(this.template(this.model.toJSON()));
        return this;
    }
});

var Contact = Item.extend({
});

var ContactCollection = CollectionJSON.extend({
    model:Contact,
    comparator:function (c) {
        return c.name;
    }
});

var AppView = Backbone.View.extend({
    el:$('#mainContent'),
    initialize:function (attrs) {
        this.events = new EventCollection();
        this.events.bind('reset', this.updateEvents, this);
        this.events.fetch({url:attrs.url});
    },
    updateEvents:function () {
        var content = $('#content');
        content.html("");
        this.events.each(function (row) {
            var rowView = new EventView({model:row});
            content.append(rowView.render().el);
        });
    }
});

// Map from CRUD to HTTP for our default `Backbone.sync` implementation.
var methodMap = {
    'create':'POST',
    'update':'PUT',
    'delete':'DELETE',
    'read':'GET'
};

Backbone.sync = function (method, model, options) {
    // Default options, unless specified.
    options || (options = {});

    var type = methodMap[method];

    // Default JSON-request options.
    var params = {type:type, dataType:'json'};

    // Ensure that we have the appropriate request data.
    if (!options.data && model && (method == 'create' || method == 'update')) {
        var json = model.toJSON();
        var data = [];
        for (key in json.data) {
            if (json.data.hasOwnProperty(key)) {
                console.log(key);
                var value = json.data[key];
                var obj = { name:key };
                obj[_.isArray(value) ? "array" : _.isObject(value) ? "object" : "value"] = value;
                data.push(obj);
            }
        }
        var template = {template:{
            data:data
        }}
        console.log(template);
        params.contentType = 'application/collection+json';
        params.data = JSON.stringify(template);

    }
    if (model) {
        params.url = model.url || model.href;
    }

    return $.ajax(_.extend(params, options));
}

/*if (_.isArray(value)) {
 obj.array = value;
 }
 else if (_.isObject(value)) {
 obj.object = value;
 }
 else {
 obj.value = value;
 }*/