var sessionHelpers = {};

sessionHelpers.mapState = function(state) {
    var object = {
        name: state
    }

    switch(state) {
        case "approved":
            object.icon = "icon-thumbs-up";
            break;
        case "rejected":
            object.icon = "icon-thumbs-down";
            break;
        case "pending":
        default:
            object.icon = "icon-repeat";
    }

    return object;
}

sessionHelpers.mapFormat = function(format) {
    var object = {
        name: format
    }
    switch(format) {
        case "lightning-talk":
            object.icon = "icon-bolt";
            break;
        case "panel":
            object.icon = "icon-group";
            break;
        case "bof":
            object.icon = "icon-comments";
            break;
        case "presentation":
        default:
            object.icon = "icon-comment-alt";
    }

    return object;
}

sessionHelpers.mapLevel = function(level) {
    var object = {
        name: level
    }

    switch (level) {
        case "beginner":
            object.icons = ["icon-star", "icon-star-empty", "icon-star-empty", "icon-star-empty", "icon-star-empty"];
            break;
        case "beginner_intermediate":
            object.icons = ["icon-star", "icon-star", "icon-star-empty", "icon-star-empty", "icon-star-empty"]
            break;
        case "intermediate":
            object.icons = ["icon-star", "icon-star", "icon-star", "icon-star-empty", "icon-star-empty"]
            break;
        case "intermediate_advanced":
            object.icons = ["icon-star", "icon-star", "icon-star", "icon-star", "icon-star-empty"]
            break;
        case "advanced":
            object.icons = ["icon-star","icon-star","icon-star","icon-star","icon-star"]
            break;
    }
    return object;
}
