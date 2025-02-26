/**
 * Copyright (c) Codice Foundation
 *
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 *
 **/
/*global require*/
var Marionette = require('marionette');
var template = require('./slideout.hbs');
var CustomElements = require('js/CustomElements');
var $ = require('jquery');
var router = require('component/router/router');
var Common = require('js/Common');

var componentName = 'slideout';

module.exports = Marionette.LayoutView.extend({
    template: template,
    tagName: CustomElements.register(componentName),
    events: {
        'click': 'handleOutsideClick',
        'keydown': 'handleSpecialKeys'
    },
    regions: {
        'slideoutContent': '.slideout-content'
    },
    initialize: function() {
        $('body').append(this.el);
        this.listenForClose();
        this.listenForEscape();
        this.listenTo(router, 'change', this.close);
    },
    listenForEscape: function() {
        $(window).on('keydown.'+CustomElements.getNamespace()+componentName, this.handleSpecialKeys.bind(this));
    },
    listenForClose: function() {
        this.$el.on('closeSlideout.' + CustomElements.getNamespace(), function() {
            this.close();
        }.bind(this));
    },
    open: function() {
        this.$el.toggleClass('is-open', true);
    },
    handleOutsideClick: function(event) {
        if (event.target === this.el.children[0]) {
            this.close();
        }
    },
    close: function() {
        this.$el.toggleClass('is-open', false);
        this.emptyContent();
    },
    emptyContent: function(){
        setTimeout(function() {
            this.slideoutContent.empty();
        }.bind(this), Common.coreTransitionTime*1.1);
    },
    updateContent: function(view) {
        this.slideoutContent.show(view);
    },
    handleSpecialKeys: function(event) {
        var code = event.keyCode;
        if (event.charCode && code == 0)
            code = event.charCode;
        switch (code) {
            case 27:
                // Escape
                event.preventDefault();
                this.handleEscape();
                break;
        }
    },
    handleEscape: function() {
        this.close();
    }
});