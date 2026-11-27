# 16. Search misses accented names
Status: resolved
Type: task
Spec: B12

Fold with `Normalizer` NFD and drop combining marks on both the query and the label. Unit test: "cafe" finds "Café", "tele" finds "Télé".

## Answer

AppSearch.fold: NFD, combining marks dropped, then lower case, on the query and the name. AppSearchTest.accentsDoNotGetInTheWay.
