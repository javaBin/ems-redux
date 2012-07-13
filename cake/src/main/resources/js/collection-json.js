//var _ = require('underscore');

/**
 * Takes any object and make it look like a collection+json object with utility methods.
 */
function fromObject(root) {
  root = _.isObject(root) ? root : {};
  root.collection = _.isObject(root.collection) ? root.collection : {};
  var c = root.collection;
  c.version = _.isString(c.version) ? c.version : "1.0";

  c.items = _.isArray(c.items) ? c.items : [];
  _.each(c.items, function(item) {
    item.links = _.isArray(item.links) ? item.links : [];
    item.toObject = function() {
      return _.reduce(item.data, function(map, field) {
        map[field.name] = field.value;
        return map;
      }, {});
    }
  });

  c.links = _.isArray(c.links) ? c.links : [];

  c.queries = _.isArray(c.queries) ? c.queries : [];
  _.each(c.queries, function(query) {
    query.data = _.isArray(query.data) ? query.data : [];
  });

  c.template = _.isObject(c.template) ? c.template : undefined;
  // c.templates = _.isArray(c.templates) ? c.templates : [];

  // TODO: make un-enumerable
  root.mapItems = function(f) {
    return _.map(this.collection.items, function(item) {
      return f(item.toObject());
    });
  };
  root.findLinkByRel = function(rel) {
    return _.find(this.collection.links, function(link) {
      return rel === link.rel;
    });
  }
  root.isCollection = function() {
    return typeof this.collection == 'object';
  }
  root.isTemplate = function() {
    return typeof this.template == 'object';
  }

  return root;
}
//module.exports.fromObject = fromObject;

function fromString(s) {
  var object = {};
  try {
    object = JSON.parse(s);
  }
  catch(ex) {
  }
  return fromObject(object);
}
//module.exports.fromString = fromString;
