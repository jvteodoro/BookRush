# ${{ values.name }}

${{ values.description }}

[TechDocs](docs/index.md) · [Catalog](catalog-info.yaml) · [Contracts](api/README.md)

Run `docker build -t ${{ values.name }} .` then `docker run --rm -p 8080:8080 ${{ values.name }}`.
Register catalog-info.yaml in the platform root Location after merging the generated PR.
