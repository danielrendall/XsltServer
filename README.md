# XSLT Server

An experiment in Scala 3. It's a server to run XSLT transformations (any variant of XSLT supported by Saxon-HE)

## Usage

1. Run it on some port or other (default 8080)
2. Register one (or more) XSLTs for it to run:
```shell
curl -XPUT --http1.0 --data @my_xslt.xsl http://localhost:8080/endpoint_for_xslt
```
3. Post XML to it to be converted:
```shell
curl -XPOST --http1.0 --data @an_xml_file.xml http://localhost:8080/endpoint_for_xslt > transformed_output.xml
```
4. When you're finished, tell it to quit
```shell
curl -XGET http://localhost:8080/_quit
```

## Notes

If you are using `curl`, you need to set with --http1.0 or --expect100-timeout 0.01, otherwise you will get annoying
1s delays with each request as it waits in vain for the server to respond with a "100: Continue". See
https://daniel.haxx.se/blog/2020/02/27/expect-tweaks-in-curl/ for some information about this.

## TODO

Tests, proper documentation, deciding a license.

This is _not_ intended for serious production use!
