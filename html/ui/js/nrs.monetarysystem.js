/**
 * @depends {nrs.js}
 */
var NRS = (function(NRS, $, undefined) {

	/* CURRENCIES PAGE */
	NRS.pages.currencies = function() {
		NRS.sendRequest("getAllCurrencies+", {
			"account": NRS.accountRS,
			"firstIndex": NRS.pageNumber * NRS.itemsPerPage - NRS.itemsPerPage,
			"lastIndex": NRS.pageNumber * NRS.itemsPerPage
		}, function(response, input) {
			if (response.currencies && response.currencies.length) {
				if (response.currencies.length > NRS.itemsPerPage) {
					NRS.hasMorePages = true;
					response.currencies.pop();
				}
				var rows = "";
				for (var i = 0; i < response.currencies.length; i++) {
               var currency = response.currencies[i];
					rows += "<tr><td><a href='#' data-transaction='" + String(currency.currency).escapeHTML() + "'>" + String(currency.currency).escapeHTML() + "</a></td>" +
                  "<td>" + currency.name + "</td>" +
                  "<td>" + currency.code + "</td>" +
                  "<td>" + currency.type + "</td>" +
                  "</tr>";
				}
				NRS.dataLoaded(rows);
			} else {
				NRS.dataLoaded();
			}
		});
	};
	
	/* EXCHANGE HISTORY PAGE */
	NRS.pages.exchange_history = function() {
		NRS.sendRequest("getAllExchanges+", {
			"firstIndex": NRS.pageNumber * NRS.itemsPerPage - NRS.itemsPerPage,
			"lastIndex": NRS.pageNumber * NRS.itemsPerPage
		}, function(response, input) {
			if (response.exchanges && response.exchanges.length) {
				if (response.exchanges.length > NRS.itemsPerPage) {
					NRS.hasMorePages = true;
					response.exchanges.pop();
				}
				var rows = "";
				for (var i = 0; i < response.exchanges.length; i++) {
                var exchange = response.exchanges[i];
					rows += "<tr><td><a href='#' data-transaction='" + String(exchange.exchange).escapeHTML() + "'>" + String(exchange.exchange).escapeHTML() + "</a></td>" +
                  "<td>" + exchange.name + "</td>" +
                  "<td>" + exchange.code + "</td>" +
                  "<td>" + exchange.type + "</td>" +
                  "</tr>";
				}
				NRS.dataLoaded(rows);
			} else {
				NRS.dataLoaded();
			}
		});
	};
	
	/* Calculate correct fees based on currency code length */
	$("#issue_currency_code").keyup(function(e) {
		if($("#issue_currency_code").val().length < 4){
			$("#issue_currency_fee").val("25000");
			$("#issue_currency_modal .advanced_fee").html("25'000 NXT");
		}
		else if($("#issue_currency_code").val().length == 4){
			$("#issue_currency_fee").val("1000");
			$("#issue_currency_modal .advanced_fee").html("1'000 NXT");
		}
		else {
			$("#issue_currency_fee").val("40");
			$("#issue_currency_modal .advanced_fee").html("40 NXT");
		}
		this.value = this.value.toLocaleUpperCase();
	});
	$("#issue_currency_code").blur(function(e) {
		if($("#issue_currency_code").val().length < 4){
			$("#issue_currency_fee").val("25000");
			$("#issue_currency_modal .advanced_fee").html("25'000 NXT");
		}
		else if($("#issue_currency_code").val().length == 4){
			$("#issue_currency_fee").val("1000");
			$("#issue_currency_modal .advanced_fee").html("1'000 NXT");
		}
		else {
			$("#issue_currency_fee").val("40");
			$("#issue_currency_modal .advanced_fee").html("40 NXT");
		}
		this.value = this.value.toLocaleUpperCase();
	});
	
	NRS.forms.issueCurrency = function($modal) {
		var data = NRS.getFormData($modal.find("form:first"));

		data.description = $.trim(data.description);

		if (!data.description) {
			return {
				"error": $.t("error_description_required")
			};
		} else if (!data.name) {
			return {
				"error": $.t("error_name_required")
			};
		} else if (!data.code || data.code.length < 3) {
			return {
				"error": $.t("error_code_required")
			};
		} else if (!data.totalSupply || data.totalSupply < 1) {
			return {
				"error": $.t("error_type_supply")
			};
		} else {
			return {
				"data": data
			};
		}
	}
	
	$('#issue_currency_reservable').change(function() {
        if($(this).is(":checked"))
            $( ".optional_reserve" ).show();
		else
			$( ".optional_reserve" ).hide();
    });
    $('#issue_currency_mintable').change(function() {
        if($(this).is(":checked"))
            $( ".optional_mint" ).show();
		else
			$( ".optional_mint" ).hide();
    });

   return NRS;
}(NRS || {}, jQuery));