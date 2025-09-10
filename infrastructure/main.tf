provider "azurerm" {
  features {}
}

locals {
  vaultName = "${var.product}-${var.env}"
}

resource "azurerm_resource_group" "rg" {
  name     = "${var.product}-${var.component}-${var.env}"
  location = var.location

  tags = var.common_tags
}

module "application_insights" {
  source = "git@github.com:hmcts/terraform-module-application-insights?ref=4.x"

  env      = var.env
  product  = var.product
  name     = "${var.product}-${var.component}-appinsights"
  location = var.appinsights_location

  resource_group_name = azurerm_resource_group.rg.name

  common_tags = var.common_tags
}
