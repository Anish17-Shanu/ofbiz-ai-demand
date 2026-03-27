import pathlib

import pandas as pd

from model import load_model


def write_order_lines(path: pathlib.Path) -> None:
    df = pd.DataFrame(
        {
            "orderId": ["O1", "O2", "O3"],
            "orderDate": ["2024-01-01", "2024-01-02", "2024-01-03"],
            "productId": ["P1", "P1", "P2"],
            "quantity": [10, 20, 5],
            "facilityId": ["F1", "F1", "F2"],
        }
    )
    df.to_csv(path, index=False)


def test_predict_basic(tmp_path: pathlib.Path):
    base = tmp_path / "ofbiz-framework" / "runtime" / "data" / "export"
    base.mkdir(parents=True)
    order_lines = base / "order_lines.csv"
    write_order_lines(order_lines)

    model = load_model(tmp_path / "ofbiz-framework", window=2, model_version="test")
    result = model.predict("P1", 7)

    assert result["avg_daily"] > 0
    assert result["total"] > 0
    assert result["history_days"] == 2


def test_predict_no_history_returns_schema_fields(tmp_path: pathlib.Path):
    base = tmp_path / "ofbiz-framework" / "runtime" / "data" / "export"
    base.mkdir(parents=True)
    pd.DataFrame(columns=["orderId", "orderDate", "productId", "quantity", "facilityId"]).to_csv(
        base / "order_lines.csv",
        index=False,
    )
    pd.DataFrame(
        {
            "productId": ["P1"],
            "facilityId": ["F1"],
            "minimumStock": [7],
            "reorderQuantity": [20],
            "lastInventoryCountDate": [""],
        }
    ).to_csv(base / "product_facility.csv", index=False)
    pd.DataFrame(
        {
            "inventoryItemId": ["I1"],
            "productId": ["P1"],
            "facilityId": ["F1"],
            "quantityOnHandTotal": [3],
            "availableToPromiseTotal": [2],
        }
    ).to_csv(base / "inventory_items.csv", index=False)

    model = load_model(tmp_path / "ofbiz-framework", window=2, model_version="test")
    result = model.predict("P1", 7)

    assert result["total"] == 0.0
    assert result["on_hand"] == 3.0
    assert result["available_to_promise"] == 2.0
    assert result["min_stock"] == 7.0
    assert result["reorder_qty"] == 20.0
    assert result["stock_gap"] == 4.0
    assert result["forecast_method"] == "no_history"
