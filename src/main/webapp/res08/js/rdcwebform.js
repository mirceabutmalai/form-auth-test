/*!
 * RADCOM.
 * 
 */

/**
 * function expression for UMD module style for RDC WebForms.
 * this code needs the following Polyfill modules:
 * 1. Map + its dependencies
 * 2. Set + its dependencies
 * 3. Array.prototype.sort + its deps
 * @param {!Object} root object
 * @param {function (!Object, ?, !Object): !Object} factory function called in order to obtain exported object
 * @return {undefined} no return value
 */
(function (root, factory) {
    "use strict";

    if ((typeof define === 'function') && (define['amd'])) {
        // AMD. Register as an module.
        define(['jquery', 'jquery-ui', 'logger'], function (jQuery, jQueryUi, rLogger) {
            return factory(window, jQuery, rLogger);
        });
    } else if ((typeof exports === 'object') && (typeof exports['nodeName'] !== 'string')) {
        // CommonJS
        /** @suppress {undefinedVars} */
        var ajq = require('jquery');
        /** @suppress {undefinedVars} */
        var ajqui = require('jquery-ui');
        /** @suppress {undefinedVars} */
        var aLogger = require('logger');

        exports['RdcWebForm'] = factory(window, ajq, aLogger);
    } else {
        // Browser globals --> the most traditional, historical and safe method
        root['RdcWebForm'] = factory(window, root['jQuery'], root['RdcLogger']);
    }

})(((('object' === typeof window) && (window))
        || (('object' === typeof self) && (self))
        || (('object' === typeof global) && (global))
        || ({})), function (window, jQuery, rLogger) {

    "use strict";

    if (typeof window !== 'object') {
        throw new Error('RdcWebForm\'s JavaScript requires window global object');
    }
    if (typeof window['document'] !== 'object') {
        throw new Error('RdcWebForm\'s JavaScript requires document object in window global object');
    }
    if (typeof jQuery === 'undefined') {
        throw new Error('RdcWebForm\'s JavaScript requires jQuery');
    }
    if (typeof rLogger === 'undefined') {
        throw new Error('RdcWebForm\'s JavaScript requires RdcLogger');
    }

    const cVersion = "1.0.res08";

    const cLogLevelDebug = rLogger['levelVerbose'];
    const cLogLevelInfo = rLogger['levelInfo'];

    /**
     * @type {Object} window document object
     */
    var winDocument = window['document'];
    /**
     * @type {Object} window document location object
     */
    var winDocLocation = winDocument['location'];

    /**
     * instance sequence generator.
     * @type {!number}
     * @private
     */
    var seqInstanceGenerator = 1;

    // parametri globali
    const cAjaxReqTimeout = 10400;

    /**
     * mappings for html escaping.
     * @type {!Object}
     * @private
     */
    const cEscapeMappings = {
        '\"': '&quot;',
        '\'': '&apos;',
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;'
    };

    /**
     * @typedef {{
     *     id: !number,
     *     name: ?string
     * }}
     */
    var TypeSelectFromToItem;

    /**
     * @typedef {{
     *     dataId: !number,
     *     dataName: ?string,
     *     htmlId: !string,
     *     htmlElem: ?Object,
     *     selectedStatus: !boolean
     * }}
     */
    var TypeSelectFromToDataElement;

    /**
     * @typedef {{
     *     dataId: !number,
     *     htmlId: !string
     * }}
     */
    var TypeSelectFromToMoveDataItem;

    /**
     * @typedef {{
     *     startedFromUnselected: !boolean,
     *     dataItems: !Array<TypeSelectFromToMoveDataItem>
     * }}
     */
    var TypeSelectFromToDragDropData;

    /**
     * @typedef {{
     *     startedFromUnselected: !boolean,
     *     dataItems: !Map<number, TypeSelectFromToMoveDataItem>
     * }}
     */
    var TypeSelectFromToMoveingData;

    // -------------------------------------------------------------------
    // pure utility functions section
    // -------------------------------------------------------------------

    /**
     * escape html characters from a specified string.
     * @param {?string} pText text to perform html escaping on
     * @return {!string} replaced string value
     */
    var gEscapeHtml = function (pText) {
        if ((pText === null) || (pText === '')) {
            return '';
        }

        return pText.replace(/[\"\'&<>]/g, function (aMatched) {
            return cEscapeMappings[aMatched];
        });
    };

    /**
     * show modal form.
     * @param {!Object} pDivElem div modal element wrapped by jQuery
     * @return {undefined} no return value
     */
    var gShowModalForm = function (pDivElem) {
        pDivElem['css']('display', 'block');
    };

    /**
     * hide modal form.
     * @param {!Object} pDivElem div modal element wrapped by jQuery
     * @return {undefined} no return value
     */
    var gHideModalForm = function (pDivElem) {
        pDivElem['css']('display', 'none');
    };

    /**
     * show modal alert with message.
     * @param {!Object} pDivModalElem div modal element wrapped by jQuery
     * @param {!Object} pDivMessageElem div message element wrapped by jQuery
     * @param {null|string|Array<string>} pMsgData message data to be shown
     * @return {undefined} no return value
     */
    var gShowModalAlertWithMessage = function(pDivModalElem, pDivMessageElem, pMsgData) {
        var aIdOfDivMessage = pDivMessageElem['id'];
        pDivMessageElem['empty']();

        if (pMsgData !== null) {
            if ((jQuery['isArray'](pMsgData)) && (pMsgData.length > 0)) {
                var aMessagesArray = pMsgData;

                var myInnerHtml = '';
                myInnerHtml += '<ol>';
                jQuery['each'](aMessagesArray, function (aIdx, aMsg) {
                    var aIdOfLi = 'idLiMsg_' + aIdx + + '_' + aIdOfDivMessage;
                    myInnerHtml += '<li id="' + aIdOfLi + '">' + gEscapeHtml(aMsg) + '</li>';
                });
                myInnerHtml += '</ol>';

                pDivMessageElem['html'](myInnerHtml);
            } else {
                pDivMessageElem['text'](pMsgData);
            }
        }

        gShowModalForm(pDivModalElem);
    };

    /**
     * hide modal alert with message.
     * @param {!Object} pDivModalElem div modal element wrapped by jQuery
     * @param {!Object} pDivMessageElem div message element wrapped by jQuery
     * @return {!boolean} always false value is returned
     */
    var gHideModalAlertWithMessage = function (pDivModalElem, pDivMessageElem) {
        gHideModalForm(pDivModalElem);
        pDivMessageElem['empty']();
        return false;
    };

    /**
     * complete select form data non empty options.
     * @param {!Object} pSelectFormElem select form element wrapped by jQuery
     * @return {undefined} no return value
     */
    var gClearSelectNonEmptyOptions = function (pSelectFormElem) {
        var aOptionElems = pSelectFormElem['find']('option');

        var aIterFunc = function (idx, aElem) {
            var aJqSingleOptionElem = jQuery(aElem);
            var aOptionValue = aJqSingleOptionElem['val']();

            if ((typeof aOptionValue === 'undefined') || (aOptionValue === null)
                    || (aOptionValue === '')) {
                return true;
            }

            aJqSingleOptionElem['remove']();
        };
        aOptionElems['each'](aIterFunc);
    };

    /**
     * complete form data from json data.
     * @param {!Object} pFormElem form element wrapped by jQuery
     * @param {!Object} pJsonData json data to take values from
     * @return {undefined} no return value
     */
    var gCompleteForm = function(pFormElem, pJsonData) {
        jQuery['each'](pJsonData, function (aFieldName, aFieldValue) {
            var aInputElems = null;

            if (jQuery['isArray'](aFieldValue)) {
                aInputElems = pFormElem['find']('select[name="' + aFieldName + '"]');

                var aIterFunc1 = function (idx, aElem) {
                    var aJqElem = jQuery(aElem);
                    gClearSelectNonEmptyOptions(aJqElem);
                };
                aInputElems['each'](aIterFunc1);

                var aLastOptionSelectedValue = null;
                var aIterFunc2 = function (aSubIdx, aOneValue) {
                    if (typeof aOneValue === 'object') {
                        var aOptionValue = aOneValue['id'];
                        var aOptionName = aOneValue['name'];
                        var aOptionSelected = aOneValue['selected'];

                        if ((typeof aOptionValue !== 'undefined') && (aOptionValue !== null)) {
                            if (typeof aOptionValue !== 'string') {
                                aOptionValue = '' + aOptionValue;
                            }
                            if (aOptionValue !== '') {
                                aInputElems['append'](new Option(aOptionName, aOptionValue));
                                if (aOptionSelected) {
                                    aLastOptionSelectedValue = aOptionValue;
                                }
                            } else {
                                aInputElems['append'](new Option(aOneValue, aOneValue));
                            }
                        } else {
                            aInputElems['append'](new Option(aOneValue, aOneValue));
                        }
                    } else {
                        aInputElems['append'](new Option(aOneValue, aOneValue));
                    }
                };
                jQuery['each'](aFieldValue, aIterFunc2);

                if (aLastOptionSelectedValue !== null) {
                    aInputElems['val'](aLastOptionSelectedValue);
                }

                aInputElems['change']();
                return true; // this means continue loop
            }

            aInputElems = pFormElem['find']('[name="' + aFieldName + '"][type=hidden]');
            aInputElems['val'](aFieldValue);
            aInputElems['change']();

            aInputElems = pFormElem['find']('[name="' + aFieldName + '"][type=text]');
            aInputElems['val'](aFieldValue);
            aInputElems['change']();

            aInputElems = pFormElem['find']('textarea[name="' + aFieldName + '"]');
            aInputElems['val'](aFieldValue);
            aInputElems['change']();

            aInputElems = pFormElem['find']('select[name="' + aFieldName + '"]');
            aInputElems['val'](aFieldValue);
            aInputElems['change']();

            aInputElems = pFormElem['find']('[name="' + aFieldName + '"][type=checkbox]');
            aInputElems['prop']('checked', aFieldValue);
            aInputElems['change']();

            aInputElems = pFormElem['find']('[name="' + aFieldName + '"][type=radio]');
            aInputElems['prop']('checked', aFieldValue);
            aInputElems['change']();
        });
    };

    /**
     * clear form data.
     * @param {!Object} pFormElem form element wrapped by jQuery
     * @return {undefined} no return value
     */
    var gClearForm = function(pFormElem) {
        /**
         * @type {Object}
         */
        var aInnerFormElem;

        aInnerFormElem = pFormElem['find']('input[type=hidden]');
        aInnerFormElem['val']('');
        aInnerFormElem = pFormElem['find']('input[type=text]');
        aInnerFormElem['val']('');
        aInnerFormElem = pFormElem['find']('textarea');
        aInnerFormElem['val']('');
        aInnerFormElem = pFormElem['find']('select');
        aInnerFormElem['val']('');
        aInnerFormElem = pFormElem['find']('input[type=file]');
        aInnerFormElem['val']('');
        aInnerFormElem = pFormElem['find']('input[type=checkbox]');
        aInnerFormElem['prop']('checked', false);
        aInnerFormElem = pFormElem['find']('input[type=radio]');
        aInnerFormElem['prop']('checked', false);
    };

    /**
     * check if object property has empty value.
     * @param {?Object} pObj object to check property
     * @param {?string} pProperty property name
     * @return {!boolean} true if property is empty
     */
    var gIsEmpty = function (pObj, pProperty) {
        if ('undefined' === typeof pObj) {
            return true;
        } else if (null === pObj) {
            return true;
        } else if ('undefined' === typeof pProperty) {
            return true;
        } else if (null === pProperty) {
            return true;
        } else if ('' === pProperty) {
            return true;
        } else if ('undefined' === typeof pObj[pProperty]) {
            return true;
        } else if (null === pObj[pProperty]) {
            return true;
        }
        return false;
    };

    /**
     * extract property value from object.
     * @param {?Object} pObj object to extract property
     * @param {?string} pProperty property name
     * @param {*} pDefaultValue default value
     * @return {*} extracted value
     */
    var gProp = function (pObj, pProperty, pDefaultValue) {
        if (gIsEmpty(pObj, pProperty)) {
            return pDefaultValue;
        }
        return pObj[pProperty];
    };

    /**
     * constructor for web form link with page reload.
     * @param {!Object} pClientOpt object with properties for initialization
     * @final
     * @constructor
     */
    var aWebFormLinkWithReload = function (pClientOpt) {
        if (gIsEmpty(pClientOpt, 'linkId')) {
            throw new Error('missing mandatory argument linkId');
        }
        if (gIsEmpty(pClientOpt, 'linkUrl')) {
            throw new Error('missing mandatory argument linkUrl');
        }

        /**
         * configuration options.
         * @type {!Object}
         * @private
         */
        this.options = pClientOpt;

        /**
         * html id for link html element.
         * @type {!string}
         * @private
         */
        this.linkId = /** @type {!string} */ (gProp(pClientOpt, 'linkId', ''));
        /**
         * html element wrapped by jQuery for link html element.
         * @type {!Object}
         * @private
         */
        this.linkElem = jQuery('#' + this.linkId);
        if ((this.linkElem === null) || (typeof this.linkElem === 'undefined')
                || (this.linkElem.length !== 1)) {
            throw new Error('linkId argument does not point to a valid html element');
        }

        /**
         * link async request url.
         * @type {!string}
         * @private
         */
        this.linkUrl = /** @type {!string} */ (gProp(pClientOpt, 'linkUrl', ''));

        /**
         * debug mode of operation.
         * @type {!boolean}
         * @private
         */
        this.debug = /** @type {!boolean} */ (gProp(pClientOpt, 'debug', false));

        /**
         * our instance identifier.
         * @type {!number}
         * @private
         */
        this.instanceId = seqInstanceGenerator++;

        var aLevel = (this.debug) ? cLogLevelDebug : cLogLevelInfo;
        var aCategory = '';
        aCategory += 'RdcWebForm.LinkWithReload';
        aCategory += '[' + this.linkId + ']';
        aCategory += '[' + this.instanceId + ']';

        /**
         * logger object instance.
         * @type {!Object}
         * @private
         * @suppress {checkTypes}
         */
        this.logger = new rLogger({
            'category': aCategory,
            'level': aLevel
        });

        /**
         * jQuery ajax xhr object.
         * @type {?Object}
         * @private
         */
        this.ajaxXhr = null;

        var self = this;

        /**
         * api to be exported.
         * @type {!Object}
         * @private
         */
        this.api = (function () {
            var rval = {};

            /**
             * install js logic to html elements.
             * @returns {undefined} no return value
             */
            rval['install'] = function () {
                self._install.apply(self, []);
            };

            return rval;
        })();

        return this.api;
    };

    aWebFormLinkWithReload.prototype = {};

    /**
     * install element decorator.
     * @returns {undefined} no return value
     * @this {aWebFormLinkWithReload}
     * @private
     */
    aWebFormLinkWithReload.prototype._install = function () {
        if (this.logger['isTraceEnabled']()) {
            this.logger['trace']('installing');
        }

        var self = this;
        this.linkElem['on']('click', null, null, function () {
            try {
                return self._onLinkClick.apply(self, []);
            } catch (e) {
                self.logger['error']('uncatched exception in link click event. e=' + e);
            }
        });
    };

    /**
     * link click event handler.
     * @returns {boolean} false if browser link click actions should be suppressed
     * @this {aWebFormLinkWithReload}
     * @private
     */
    aWebFormLinkWithReload.prototype._onLinkClick = function () {
        this._ajaxSendAction();
        return false;
    };

    /**
     * send ajax action.
     * @returns {undefined} no return value
     * @this {aWebFormLinkWithReload}
     * @private
     */
    aWebFormLinkWithReload.prototype._ajaxSendAction = function () {
        if (this.ajaxXhr !== null) {
            this.ajaxXhr['abort']();
            this.ajaxXhr = null;
        }

        this.ajaxXhr = jQuery['ajax'](this.linkUrl, {
            'method': 'POST',
            'cache': false,
            'processData': false,
            'dataType': 'json',
            'timeout': cAjaxReqTimeout
        });

        var self = this;
        this.ajaxXhr['done'](function (data, textStatus, jqXhr) {
            try {
                self._ajaxDoneResponse.apply(self, [data, textStatus, jqXhr]);
            } catch (e) {
                self.logger['error']('uncatched exception in ajax done response event. e=' + e);
            }
        });
        this.ajaxXhr['fail'](function (jqXhr, textStatus, errorThrown) {
            try {
                self._ajaxFailResponse.apply(self, [jqXhr, textStatus, errorThrown]);
            } catch (e) {
                self.logger['error']('uncatched exception in ajax fail response event. e=' + e);
            }
        });
    };

    /**
     * ajax done response handler.
     * @param {Object} pData data as parsed json return by ajax request
     * @param {string} pTextStatus text status as delivered by jQuery
     * @param {Object} pJqXhr ajax xhr object decorated by jQuery
     * @returns {undefined} no return value
     * @this {aWebFormLinkWithReload}
     * @private
     */
    aWebFormLinkWithReload.prototype._ajaxDoneResponse = function (pData, pTextStatus, pJqXhr) {
        if (pJqXhr !== this.ajaxXhr) {
            this.logger['error']('ajax xhr does not match in done response');
            return;
        }

        this.ajaxXhr = null;

        if (this.logger['isTraceEnabled']()) {
            this.logger['trace']('ajax done response received: status=' + pJqXhr['status']
                    + ', textStatus=' + pTextStatus + ', data=' + pData);
        }

        var httpStatusCodeClass = Math.floor(pJqXhr['status'] / 100);

        if (httpStatusCodeClass !== 2) {
            this.logger['log']('received failure http status response code ' + pJqXhr['status']
                    + ' for ajax action request');
            return;
        }

        if (this.logger['isTraceEnabled']()) {
            this.logger['trace']('received success http status response');
        }
        winDocLocation['reload']();
    };

    /**
     * ajax fail response handler.
     * @param {Object} pJqXhr the ajax xhr object decorated by jQuery
     * @param {string} pTextStatus text status for ajax failure
     * @param {string} pErrorThrown error thrown by ajax failure
     * @returns {undefined} no return value
     * @this {aWebFormLinkWithReload}
     * @private
     */
    aWebFormLinkWithReload.prototype._ajaxFailResponse = function (pJqXhr, pTextStatus, pErrorThrown) {
        if (pJqXhr !== this.ajaxXhr) {
            this.logger['error']('ajax xhr does not match in fail response');
            return;
        }

        this.ajaxXhr = null;

        if (pJqXhr['status'] === 401) {
            if (this.logger['isTraceEnabled']()) {
                this.logger['trace']('detected that server may require re-authentication ==> page reload');
            }
            winDocLocation['reload']();
            return;
        }

        this.logger['log']('ajax fail response received: status=' + pJqXhr['status']
                + ', textStatus=' + pTextStatus + ', errorThrown=' + pErrorThrown);
    };

    /**
     * attributes for web forms.
     * @type {!Map<string, Object>}
     * @private
     */
    var gStoredObjects = new Map();

    /**
     * @type {Object} web form global object value
     * @public
     */
    var aWebForm = {};

    aWebForm['LinkWithReload'] = aWebFormLinkWithReload;

    /**
     * set attribute on global web form object.
     * @param {string} pKey attribute key value
     * @param {Object} pValue attribute value
     * @returns {undefined} no return value
     * @public
     */
    aWebForm['setAttribute'] = function(pKey, pValue) {
        if ((typeof pKey !== 'string') || (pKey === null) || (pKey === '')) {
            return;
        }

        if (gStoredObjects.has(pKey)) {
            gStoredObjects.delete(pKey);
        }
        gStoredObjects.set(pKey, pValue);
    };

    /**
     * get attribute by key value.
     * @param {string} pKey attribute key to search for
     * @returns {?Object} attribute value or null if attribute is not found
     * @public
     */
    aWebForm['getAttribute'] = function(pKey) {
        if ((typeof pKey !== 'string') || (pKey === null) || (pKey === '')) {
            return null;
        }

        if (!gStoredObjects.has(pKey)) {
            return null;
        }

        return gStoredObjects.get(pKey);
    };

    /**
     * remove attribute with specified key.
     * @param {string} pKey attribute key to be removed
     * @returns {undefined} no return value
     * @public
     */
    aWebForm['removeAttribute'] = function(pKey) {
        if ((typeof pKey !== 'string') || (pKey === null) || (pKey === '')) {
            return;
        }

        if (!gStoredObjects.has(pKey)) {
            return;
        }

        gStoredObjects.delete(pKey);
    };

    aWebForm.prototype = {};

    /**
     * object version.
     * @type {!string}
     * @public
     */
    aWebForm['VERSION'] = cVersion;

    return aWebForm;
});
