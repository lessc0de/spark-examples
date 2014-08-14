/*
Copyright 2014 Google Inc. All rights reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.google.cloud.genomics.spark.examples.rdd

import scala.collection.JavaConversions.asScalaIterator
import scala.collection.JavaConversions.seqAsJavaList

import com.google.api.services.genomics.Genomics
import com.google.api.services.genomics.model.SearchVariantsRequest
import com.google.api.services.genomics.model.{Variant => VariantModel}

/**
 * Performs the search request and provides the resultant variants.
 */
class VariantsIterator(service: Genomics, part: VariantsPartition)
    extends Iterator[(VariantKey, Variant)] {
  // The next page token for the query. If the results span multiple
  // pages, this will hold the next page token. If None, the search is
  // exhausted and so this iterator.
  private var token: Option[String] = Some("")

  // Perform the initial query and establish the iterator.
  private var it = refresh()

  // Executes the search query and returns an iterator to the variants.
  // If the query data is exhausted (i.e. no more pages) the iterator
  // will be empty.
  private def refresh(): Iterator[VariantModel] = {
    token.map { t =>
      val req = new SearchVariantsRequest()
        .setDatasetId(part.dataset)
        .setContig(part.contig)
        .setStartPosition(java.lang.Long.valueOf(part.start))
        .setEndPosition(java.lang.Long.valueOf(part.end))

      if (t.length > 0) { req.setPageToken(t) }
      req
    }
      .map { service.variants().search(_).execute() }
      .map { resp =>
        token = resp.getNextPageToken() match {
          case null => None
          case tok => Some(tok)
        }
        resp.getVariants() match {
          case null => None
          case r => Some(asScalaIterator(r.iterator()))
        }
      }
      .flatten
      .getOrElse(List[VariantModel]().iterator())
  }

  override def hasNext: Boolean = {
    if (it.hasNext) {
      true
    } else {
      it = refresh()
      it.hasNext
    }
  }

  override def next(): (VariantKey, Variant) = {
    val r = it.next()
    VariantBuilder.fromJavaVariant(r)
  }
}