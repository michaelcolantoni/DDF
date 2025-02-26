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
/*global define*/
define([
    'marionette',
    'underscore',
    'jquery',
    './content-title.hbs',
    'js/CustomElements',
    'js/store'
], function (Marionette, _, $, template, CustomElements, store) {

    return Marionette.ItemView.extend({
        setDefaultModel: function(){
            this.model = store.get('content');
        },
        events: {
            'change input': 'updateWorkspaceName',
            'keyup input': 'updateWorkspaceName',
            'keydown input': 'updateWorkspaceName'
        },
        template: template,
        tagName: CustomElements.register('content-title'),
        initialize: function (options) {
            if (options.model === undefined){
                this.setDefaultModel();
            }
            this.listenTo(this.model, 'change:currentWorkspace', this.handleChange);
        },
        onRender: function(){
        },
        handleChange: function(){
            this.updateInput();
        },
        updateInput: function(){
            if (this.model.get('currentWorkspace')) {
                var input = this.$el.find('input');
                var currentTitle = this.model.get('currentWorkspace').get('title');
                if (input.val() !== currentTitle){
                    input.val(currentTitle);
                }
            }
        },
        updateWorkspaceName: function(e){
            this.model.get('currentWorkspace').set('title', e.currentTarget.value);
        }
    });
});
