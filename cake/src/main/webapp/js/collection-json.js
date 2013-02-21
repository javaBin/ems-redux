
function toObject(data) {
  return function() {
    return _.reduce(data, function(map, field) {
      if (_.isArray(field.array)) {
        map[field.name] = field.array;
      }
      else if (_.isObject(field.object)) {
        map[field.name] = field.object;
      }
      else {
        map[field.name] = field.value;
      }

      return map;
    }, {});
  }
}

function findLinkByRel(obj, rel) {
  return _.find(obj.links, function(link) {
    return rel === link.rel;
  });
}

function findLinksByRel(obj, rel) {
  return _.filter(obj.links, function(link) {
    return rel === link.rel;
  });
}

function findQueryByRel(obj, rel) {
  return _.find(obj.queries, function(query) {
    return rel === query.rel;
  });
}

function expandQuery(query, params) {
  var uri = URI(query.href)
  uri.addQuery(params);
  return uri.toString();
}

function fixTemplate(t) {
  t.data = _.isArray(t.data) ? t.data : [];
  t.toObject = toObject(t.data);
}

/**
 * Takes any object and make it look like a collection+json collection object.
 */
function toCollection(root) {
  //console.log('in ', root);

  root.isCollection = function() {
    return _.isObject(this.collection);
  }
  root.isTemplate = function() {
    return _.isObject(this.template);
  }

  if(root.isTemplate()) {
    fixTemplate(root.template);
  }
  else {
    root = _.isObject(root) ? root : {collection: {}};
    if(!_.isObject(root.collection)) {
      root.collection = {};
    }
    var c = root.collection;

    c.version = _.isString(c.version) ? c.version : "1.0";
    c.items = _.isArray(c.items) ? c.items : [];
    _.each(c.items, function(item) {
      item.data = _.isArray(item.data) ? item.data : [];
      item.links = _.isArray(item.links) ? item.links : [];
      item.toObject = toObject(item.data);
      item.findLinkByRel = function(rel) {
        return findLinkByRel(item, rel);
      };
      item.findLinksByRel = function(rel) {
        return findLinksByRel(item, rel);
      };

    });

    c.links = _.isArray(c.links) ? c.links : [];

    c.queries = _.isArray(c.queries) ? c.queries : [];
    _.each(c.queries, function(query) {
      query.data = _.isArray(query.data) ? query.data : [];
      query.toObject = toObject(query.data);
      query.expand = function(obj) {
        return expandQuery(query, obj);
      };
    });

    if(_.isObject(c.template)) {
      fixTemplate(c.template);
    }
    else {
      delete c.template;
    }
  }

  root.mapItems = function(f) {
    return _.map(this.collection.items, function(item) {
      return f(item);
    });
  }

  root.findLinkByRel = function(rel) {
    return findLinkByRel(c, rel);
  }

  root.findQueryByRel = function(rel) {
    return findQueryByRel(c, rel);
  }

  root.headItem = function() {
    return _.head(c.items)
  }

  return root;
}
